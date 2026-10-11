#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform mat4 InverseViewProjection;
uniform vec3 CameraOffset;
uniform vec3 SunDirection;
uniform float Strength;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec3 p) {
    p = mod(p, 128.0);
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 p) {
    vec3 cell = floor(p);
    vec3 t = fract(p);
    t = t * t * (3.0 - 2.0 * t);
    return mix(mix(mix(hash(cell), hash(cell + vec3(1, 0, 0)), t.x),
                   mix(hash(cell + vec3(0, 1, 0)), hash(cell + vec3(1, 1, 0)), t.x), t.y),
               mix(mix(hash(cell + vec3(0, 0, 1)), hash(cell + vec3(1, 0, 1)), t.x),
                   mix(hash(cell + vec3(0, 1, 1)), hash(cell + vec3(1, 1, 1)), t.x), t.y), t.z);
}

void main() {
    vec4 source = texture(DiffuseSampler, texCoord);
    float depth = texture(DepthSampler, texCoord).r;
    // Both passes preserve world depth for the hand and subsequent render hooks.
    gl_FragDepth = depth;
    fragColor = source;
    if (Strength <= 0.0) return;

    vec4 unprojected = InverseViewProjection * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 relative = unprojected.xyz / max(abs(unprojected.w), 0.000001) * sign(unprojected.w);
    // Evaluate derivatives before any non-uniform early return (sky silhouettes).
    vec3 dx = dFdx(relative);
    vec3 dy = dFdy(relative);
    vec3 crossNormal = cross(dx, dy);
    vec3 normal = crossNormal * inversesqrt(max(dot(crossNormal, crossNormal), 1e-20));
    normal *= dot(normal, -relative) < 0.0 ? -1.0 : 1.0;
    float footprint = max(length(dx), length(dy));

    if (depth >= 0.9999999) return;

    float light = dot(source.rgb, vec3(0.2126, 0.7152, 0.0722));
    float peak = max(source.r, max(source.g, source.b));
    float chroma = (peak - min(source.r, min(source.g, source.b))) / max(peak, 0.001);
    // Scene color and depth cannot distinguish ores or structures from terrain.
    float neutral = 1.0 - smoothstep(0.10, 0.32, chroma);
    float readable = smoothstep(0.035, 0.18, light) * (1.0 - smoothstep(0.78, 0.98, light));
    float amount = Strength * neutral * readable;
    if (amount <= 0.0) return;

    vec3 world = relative + CameraOffset;
    float broad = noise(world / 32.0) - 0.5;
    float brokenRock = noise(world / 4.0) - 0.5;
    // Fade each small octave before its wavelength falls below a few pixels.
    float detailFade = 1.0 - smoothstep(0.025, 0.12, footprint);
    float grain = (noise(world * 8.0) - 0.5) * detailFade;
    float rockFade = 1.0 - smoothstep(0.8, 3.0, footprint);
    float albedo = 1.0 + 0.30 * broad + 0.14 * brokenRock * rockFade + 0.10 * grain;

    float daylight = smoothstep(-0.05, 0.25, SunDirection.y);
    float facingSun = max(dot(normal, SunDirection), 0.0);
    float facingEye = max(dot(normal, normalize(-relative)), 0.0);
    // A restrained rough-dust response: stronger slope separation and a broad
    // backscatter lift when viewing down-sun. No metallic glints or fake haze.
    float backscatter = pow(max(dot(normalize(-relative), SunDirection), 0.0), 8.0);
    float diffuse = facingSun / max(facingSun + facingEye, 0.15);
    float lighting = mix(1.0, 0.80 + 0.34 * diffuse + 0.10 * backscatter * facingSun, daylight);
    float contrast = (light - 0.38) * 0.10 * daylight;
    vec3 shaded = source.rgb * albedo * lighting + vec3(contrast);
    // Preserve chromatic lights and authored colors; the lunar palette stays neutral.
    fragColor = vec4(clamp(mix(source.rgb, shaded, amount), 0.0, 1.0), source.a);
}
