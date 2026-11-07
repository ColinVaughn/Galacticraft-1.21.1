#version 150

uniform sampler2D DepthSampler;
uniform sampler2D VolumeSampler;
uniform sampler2D NoiseSampler;
uniform mat4 InverseViewProjection;
uniform vec3 CameraOffset;
uniform vec3 BoundsMin;
uniform vec3 BoundsMax;
uniform vec3 SunDirection;
uniform float CloudTime;
uniform float BandOffset;

in vec2 texCoord;
out vec4 fragColor;

float noise(vec3 p) {
    vec3 cell = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    vec2 uv = (cell.xy + f.xy + vec2(37, 17) * cell.z + 0.5) / 32.0;
    vec2 planes = texture(NoiseSampler, uv).rg;
    return mix(planes.r, planes.g, f.z);
}

float slice(vec2 p, float z) {
    vec2 base = vec2(mod(z, 16.0) * 256.0, floor(z / 16.0) * 384.0);
    float a = texture(VolumeSampler, (base + vec2(min(p.x, 255.0), p.y) + 0.5) / vec2(4096, 6144)).r;
    // Linear atlas filtering cannot wrap an individual slice's X edge on its own.
    if (p.x > 255.0) {
        float b = texture(VolumeSampler, (base + vec2(0, p.y) + 0.5) / vec2(4096, 6144)).r;
        a = mix(a, b, p.x - 255.0);
    }
    return a;
}

float volume(vec3 p) {
    if (p.y < -64.0 || p.y >= 320.0) return 0.0;
    vec3 v = vec3(mod(p.x - 0.5, 256.0), clamp(p.y + 63.5, 0.0, 383.0), mod(p.z - 0.5, 256.0));
    float z = floor(v.z);
    return mix(slice(v.xy, z), slice(v.xy, mod(z + 1.0, 256.0)), fract(v.z));
}

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    gl_FragDepth = depth;
    fragColor = vec4(0.0);
    if (BoundsMin.y > BoundsMax.y) return;
    vec4 clip = InverseViewProjection * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1);
    vec3 relative = clip.xyz / (abs(clip.w) < 1e-7 ? 1e-7 : clip.w);
    vec3 ray = normalize(relative);
    float opaqueDistance = depth >= 0.9999999 ? 1e6 : length(relative);
    vec3 reciprocal = 1.0 / mix(vec3(1e-5), ray, greaterThan(abs(ray), vec3(1e-5)));
    vec3 a = BoundsMin * reciprocal, b = BoundsMax * reciprocal;
    vec3 entry = min(a, b), exit = max(a, b);
    float near = max(0.0, max(entry.x, max(entry.y, entry.z)));
    float far = min(min(exit.x, min(exit.y, exit.z)), min(opaqueDistance, 112.0));
    if (near >= far) return;

    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float t = near + jitter * 0.7;
    float transmittance = 1.0;
    vec3 radiance = vec3(0.0);
    float daylight = smoothstep(-0.25, 0.4, SunDirection.y);
    vec3 lightColor = mix(vec3(0.22, 0.23, 0.26), vec3(1.0, 0.97, 0.92), daylight);
    vec3 shadowColor = mix(vec3(0.08, 0.07, 0.08), vec3(0.38, 0.31, 0.27), daylight);

    // Bounded work, coarse empty-space steps and front-to-back extinction.
    for (int i = 0; i < 64; i++) {
        if (t >= far || transmittance < 0.015) break;
        vec3 p = CameraOffset + ray * t;
        float material = volume(p);
        float core = min(material * (4.0 / 3.0), 1.0);
        float stepSize = core > 0.01 ? 0.7 : 2.0;
        float n = 0.0, density = 0.0;
        // Detail is evaluated only near an actual occupied voxel, never across the empty sky.
        if (core > 0.001) {
            n = noise(p / 8.0 + vec3(CloudTime * (2.0 / 3.0), 0, CloudTime * (2.0 / 3.0)));
            float detail = noise(p / 2.0 + vec3(CloudTime * (4.0 / 3.0)));
            density = smoothstep(0.01, 0.85, core) * (0.75 + n * 0.55 + detail * 0.20);
            float cover = min(volume(p + vec3(0, 3.0, 0)) * (4.0 / 3.0), 1.0);
            float light = clamp(1.0 - cover * 0.8 + (n - 0.5) * 0.25, 0.0, 1.0);
            float frost = smoothstep(0.90, 0.99, material);
            // East-west belts use world latitude, independently of the 256-block voxel cache.
            float latitude = abs(fract((BandOffset + ray.z * t) / 2048.0) * 2.0 - 1.0);
            float belt = smoothstep(0.25, 0.75, latitude + (n - 0.5) * 0.20);
            vec3 beltColor = mix(vec3(0.72, 0.53, 0.38), vec3(0.61, 0.40, 0.30), smoothstep(0.25, 0.80, n));
            vec3 albedo = mix(vec3(0.89, 0.85, 0.76), beltColor, belt);
            albedo = mix(albedo, vec3(0.91, 0.87, 0.79), frost * 0.30);
            vec3 color = albedo * mix(shadowColor, lightColor, light);
            float rim = pow(max(dot(ray, normalize(SunDirection)), 0.0), 6.0) * (1.0 - core) * daylight;
            color += albedo * lightColor * rim * 0.25;
            float opacity = 1.0 - exp(-density * min(stepSize, far - t) * 1.1);
            radiance += transmittance * opacity * color;
            transmittance *= 1.0 - opacity;
        }
        t += stepSize;
    }
    float fade = 1.0 - smoothstep(96.0, 112.0, near);
    fragColor = vec4(radiance * fade, (1.0 - transmittance) * fade);
}