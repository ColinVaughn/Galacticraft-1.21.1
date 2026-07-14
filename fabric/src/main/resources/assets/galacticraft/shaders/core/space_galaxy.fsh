#version 150

uniform vec4 ColorModulator;
uniform float Exposure;

in vec3 skyDirection;

out vec4 fragColor;

float hash31(vec3 point) {
    return fract(sin(dot(point, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

float valueNoise(vec3 point) {
    vec3 cell = floor(point);
    vec3 local = fract(point);
    local = local * local * (3.0 - 2.0 * local);

    float n000 = hash31(cell + vec3(0.0, 0.0, 0.0));
    float n100 = hash31(cell + vec3(1.0, 0.0, 0.0));
    float n010 = hash31(cell + vec3(0.0, 1.0, 0.0));
    float n110 = hash31(cell + vec3(1.0, 1.0, 0.0));
    float n001 = hash31(cell + vec3(0.0, 0.0, 1.0));
    float n101 = hash31(cell + vec3(1.0, 0.0, 1.0));
    float n011 = hash31(cell + vec3(0.0, 1.0, 1.0));
    float n111 = hash31(cell + vec3(1.0, 1.0, 1.0));

    float low = mix(mix(n000, n100, local.x), mix(n010, n110, local.x), local.y);
    float high = mix(mix(n001, n101, local.x), mix(n011, n111, local.x), local.y);
    return mix(low, high, local.z);
}

float fbm(vec3 point) {
    float value = 0.0;
    float amplitude = 0.55;
    for (int octave = 0; octave < 4; octave++) {
        value += valueNoise(point) * amplitude;
        point = point * 2.03 + vec3(13.7, 7.1, 19.3);
        amplitude *= 0.48;
    }
    return value;
}

void main() {
    vec3 direction = normalize(skyDirection);
    vec3 galacticNorth = normalize(vec3(-0.868, 0.456, -0.198));
    vec3 galacticCenter = normalize(vec3(-0.057, -0.485, -0.873));
    float latitude = abs(dot(direction, galacticNorth));

    float broadBand = exp(-pow(latitude * 5.4, 1.45));
    float structure = mix(0.42, 1.0, fbm(direction * 5.5));
    float fineStructure = mix(0.68, 1.0, fbm(direction * 17.0 + vec3(4.0, 11.0, 2.0)));
    float centerGlow = 0.72 + 0.58 * pow(max(dot(direction, galacticCenter), 0.0), 3.0);

    // Patchy extinction creates the familiar central dust lane without turning
    // the band into a colorful fantasy nebula.
    float laneShape = exp(-pow(latitude * 25.0, 1.7));
    float dustPatches = smoothstep(0.34, 0.76, fbm(direction * 10.0 + vec3(21.0, 3.0, 9.0)));
    float extinction = 1.0 - laneShape * mix(0.44, 0.78, dustPatches);

    float adaptedVisibility = mix(0.006, 1.0, Exposure * Exposure);
    float intensity = broadBand * structure * fineStructure * centerGlow * extinction * 0.034 * adaptedVisibility;
    if (intensity < 0.00035) {
        discard;
    }

    vec3 color = mix(vec3(0.61, 0.65, 0.72), vec3(0.78, 0.76, 0.72), centerGlow - 0.72);
    // space_galaxy uses additive blending, so the source color carries intensity.
    fragColor = vec4(color * intensity, intensity) * ColorModulator;
}
