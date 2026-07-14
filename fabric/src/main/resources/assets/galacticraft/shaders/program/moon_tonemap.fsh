#version 150

uniform sampler2D DiffuseSampler;
uniform float Exposure;
uniform vec2 OutSize;

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
    source *= mix(0.93, 1.09, Exposure);
    float light = luminance(source);

    // The terrain curve below cannot distinguish a faint star from a dark
    // regolith pixel using luminance alone. Compare against samples outside the
    // largest stellar billboard so isolated point sources survive the shadow
    // crush while true empty space remains exactly black.
    vec2 sampleOffset = 3.0 / max(OutSize, vec2(1.0));
    float neighborLight = luminance(texture(DiffuseSampler, texCoord + vec2(sampleOffset.x, 0.0)).rgb);
    neighborLight += luminance(texture(DiffuseSampler, texCoord - vec2(sampleOffset.x, 0.0)).rgb);
    neighborLight += luminance(texture(DiffuseSampler, texCoord + vec2(0.0, sampleOffset.y)).rgb);
    neighborLight += luminance(texture(DiffuseSampler, texCoord - vec2(0.0, sampleOffset.y)).rgb);
    neighborLight *= 0.25;
    float pointContrast = max(light - neighborLight * 0.80, 0.0);
    float isolatedPoint = smoothstep(0.003, 0.045, pointContrast);

    // A generic GameRenderer post chain cannot rely on the main depth attachment
    // still containing the completed level. Classify visible pixels by luminance:
    // empty space remains exactly black while terrain gets hard vacuum contrast.
    float visible = smoothstep(0.012, 0.10, light);
    float curvePosition = clamp((light - 0.025) / 0.90, 0.0, 1.0);
    float airlessCurve = curvePosition * curvePosition * (3.0 - 2.0 * curvePosition);
    float contrastedLight = mix(light, airlessCurve, 0.72 * visible);
    vec3 color = source * (contrastedLight / max(light, 0.001));

    // Preserve star cores at every adaptation level. Broad, faint emission
    // such as the Milky Way is restored only as the eye adapts to darkness.
    color = mix(color, max(color, source), isolatedPoint);
    float faintEmission = smoothstep(0.0025, 0.022, light) * (1.0 - smoothstep(0.075, 0.15, light));
    color = max(color, source * faintEmission * Exposure * 0.86);

    // Remove the green/brown cast from low-chroma regolith while preserving
    // blue ice, ores, vegetation, entities, and intentionally colored pixels.
    float chroma = maximumChannel(source) - minimumChannel(source);
    float neutralRegolith = (1.0 - smoothstep(0.045, 0.22, chroma)) * visible;
    color = mix(color, vec3(luminance(color)), neutralRegolith * 0.62);

    // Cool shadow fill and warm direct sunlight separate crater relief without
    // adding atmospheric haze to an airless world.
    float illumination = smoothstep(0.15, 0.70, contrastedLight);
    vec3 shadowGrade = vec3(0.91, 0.96, 1.08);
    vec3 sunGrade = vec3(1.055, 1.025, 0.965);
    vec3 splitGrade = mix(shadowGrade, sunGrade, illumination);
    color *= mix(vec3(1.0), splitGrade, 0.52 * visible);

    // Once dark-adapted, weak regolith bounce becomes perceptible without ever
    // introducing atmospheric fog or lifting empty space above true black.
    float deepShadow = (1.0 - smoothstep(0.055, 0.22, light)) * visible;
    color += vec3(0.004, 0.006, 0.010) * deepShadow * Exposure;

    // Keep the solar disc and brightest stars neutral instead of clipping them
    // to a strongly colored square.
    float highlightNeutrality = smoothstep(0.82, 1.0, light);
    color = mix(color, vec3(luminance(color)), highlightNeutrality * 0.28);

    vec2 centered = texCoord * 2.0 - 1.0;
    float vignette = 1.0 - 0.030 * smoothstep(0.28, 1.50, dot(centered, centered));
    color *= vignette;

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
