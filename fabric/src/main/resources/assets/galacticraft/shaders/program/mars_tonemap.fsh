#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

in vec2 texCoord;

out vec4 fragColor;

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

float maximumChannel(vec3 color) {
    return max(color.r, max(color.g, color.b));
}

float minimumChannel(vec3 color) {
    return min(color.r, min(color.g, color.b));
}

void main() {
    vec3 source = texture(DiffuseSampler, texCoord).rgb;
    float depth = texture(DepthSampler, texCoord).r;
    float geometry = 1.0 - step(0.999995, depth);
    float sky = 1.0 - geometry;
    float light = luminance(source);

    // Thin suspended dust produces a softer shoulder than an airless world, but
    // very dark caves remain close to the authored exposure for playability.
    float liftedLight = pow(max(light, 0.0), 0.93);
    float caveProtection = 1.0 - smoothstep(0.025, 0.11, light);
    float surfaceExposure = geometry * (1.0 - caveProtection);
    float gradedLight = mix(light, liftedLight, surfaceExposure * 0.30);
    vec3 color = source * (gradedLight / max(light, 0.001));

    float chroma = maximumChannel(source) - minimumChannel(source);
    float neutralMaterial = 1.0 - smoothstep(0.10, 0.36, chroma);
    float coolCast = smoothstep(0.015, 0.20, source.b - source.r);
    float litTerrain = surfaceExposure * smoothstep(0.06, 0.24, light);

    // Neutral Martian stone receives a strong iron-oxide white balance. Authored
    // colors on machines, plants, entities, and ores retain their identity.
    vec3 warmBalanced = color * vec3(1.18, 1.00, 0.82);
    vec3 ironDust = gradedLight * vec3(1.24, 0.83, 0.56);
    vec3 dustyTerrain = mix(warmBalanced, ironDust, 0.48);
    float rustStrength = neutralMaterial * litTerrain * (0.48 + coolCast * 0.22);
    color = mix(color, dustyTerrain, rustStrength);

    // Raw depth is non-linear, which is useful here: most of this transition is
    // naturally concentrated in the far landscape. It suggests thin dusty air
    // without turning Mars into an opaque orange fog bank.
    float distanceDust = smoothstep(0.9970, 0.999985, depth) * litTerrain;
    vec3 distanceColor = gradedLight * vec3(1.16, 0.80, 0.58) + vec3(0.018, 0.007, 0.002);
    color = mix(color, distanceColor, distanceDust * 0.20);

    // The broad sky stays rusty. Blue-dominant pixels belong to the forward-
    // scattered solar halo and receive a clean cyan-blue separation instead.
    float blueSolarLight = smoothstep(0.015, 0.18, source.b - source.r) * sky;
    vec3 rustySky = source * vec3(1.055, 0.975, 0.90);
    color = mix(color, rustySky, sky * (1.0 - blueSolarLight) * 0.22);
    vec3 blueHalo = source * vec3(0.90, 1.035, 1.18);
    color = mix(color, blueHalo, blueSolarLight * 0.42);

    // A restrained optical falloff frames the landscape without obscuring mobs.
    vec2 centered = texCoord * 2.0 - 1.0;
    float vignette = 1.0 - 0.030 * smoothstep(0.30, 1.55, dot(centered, centered));
    color *= vignette;

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
