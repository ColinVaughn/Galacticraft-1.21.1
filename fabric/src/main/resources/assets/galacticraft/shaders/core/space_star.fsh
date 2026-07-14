#version 150

uniform vec4 ColorModulator;
uniform float Exposure;
uniform float RenderMode;
uniform float Time;
uniform float SolarDiscRatio;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float hash21(vec2 point) {
    point = fract(point * vec2(123.34, 456.21));
    point += dot(point, point + 45.32);
    return fract(point.x * point.y);
}

float valueNoise(vec2 point) {
    vec2 cell = floor(point);
    vec2 local = fract(point);
    local = local * local * (3.0 - 2.0 * local);

    float lower = mix(hash21(cell), hash21(cell + vec2(1.0, 0.0)), local.x);
    float upper = mix(hash21(cell + vec2(0.0, 1.0)), hash21(cell + vec2(1.0, 1.0)), local.x);
    return mix(lower, upper, local.y);
}

float fbm(vec2 point) {
    float value = 0.0;
    float amplitude = 0.55;
    for (int octave = 0; octave < 4; octave++) {
        value += valueNoise(point) * amplitude;
        point = mat2(1.61, 1.18, -1.18, 1.61) * point + vec2(7.3, 13.1);
        amplitude *= 0.47;
    }
    return value;
}

float ellipse(vec2 point, vec2 center, vec2 scale, float angle) {
    float cosine = cos(angle);
    float sine = sin(angle);
    vec2 offset = point - center;
    offset = mat2(cosine, -sine, sine, cosine) * offset;
    return length(offset / scale);
}

void main() {
    vec2 point = texCoord0 * 2.0 - 1.0;
    float radiusSquared = dot(point, point);
    if (radiusSquared >= 1.0) {
        discard;
    }

    if (RenderMode > 2.5) {
        // The broad K-corona is irregular and streamer-shaped. It is kept faint
        // enough to preserve true black space but becomes obvious around the
        // limb and under telescope magnification.
        float radius = sqrt(radiusSquared);
        float discRadius = clamp(SolarDiscRatio, 0.02, 0.95);
        float outside = max((radius - discRadius) / (1.0 - discRadius), 0.0);
        float limbMask = smoothstep(discRadius * 0.90, discRadius * 1.04, radius);
        float angle = atan(point.y, point.x);
        float streamerNoise = fbm(vec2(angle * 1.75 + Time * 0.0025, outside * 4.0 - Time * 0.005));
        float broadStreamers = pow(0.5 + 0.5 * cos(angle * 6.0 + streamerNoise * 5.0), 6.0);
        float fineStreamers = pow(0.5 + 0.5 * cos(angle * 13.0 - streamerNoise * 7.0), 12.0);
        float falloff = exp(-outside * mix(3.2, 5.8, streamerNoise));
        float corona = limbMask * falloff * (0.035 + broadStreamers * 0.105 + fineStreamers * 0.045);
        corona *= 1.0 - smoothstep(0.86, 1.0, radius);

        if (corona < 0.0005) {
            discard;
        }

        vec3 coronaColor = mix(vec3(1.0, 0.42, 0.08), vec3(1.0, 0.91, 0.68), exp(-outside * 6.0));
        float intensity = corona * vertexColor.a * mix(0.74, 1.08, Exposure);
        fragColor = vec4(coronaColor * vertexColor.rgb * intensity, intensity) * ColorModulator;
        return;
    }

    if (RenderMode > 1.5) {
        // Near-limb glow, plasma prominences, and a restrained optical response
        // sit between the physical photosphere and the broad corona.
        float radius = sqrt(radiusSquared);
        float discRadius = clamp(SolarDiscRatio, 0.02, 0.95);
        float outside = max((radius - discRadius) / (1.0 - discRadius), 0.0);
        float limbMask = smoothstep(discRadius * 0.82, discRadius * 1.02, radius);
        float angle = atan(point.y, point.x);

        float glare = exp(-outside * 7.5) * limbMask * (1.0 - smoothstep(0.82, 1.0, radius));
        float prominenceRing = exp(-pow((radius - discRadius * 1.075) / max(discRadius * 0.045, 0.004), 2.0));
        float prominencePattern = smoothstep(0.48, 0.77,
                fbm(vec2(angle * 2.6 + Time * 0.007, 5.0 + sin(Time * 0.004))));
        float prominences = prominenceRing * prominencePattern * limbMask;

        vec2 solarPoint = point / discRadius;
        float loopOne = 1.0 - smoothstep(0.045, 0.12,
                abs(ellipse(solarPoint, vec2(0.42, 0.91), vec2(0.31, 0.18), -0.28) - 1.0));
        loopOne *= smoothstep(0.88, 1.01, solarPoint.y) * smoothstep(1.0, 1.035, length(solarPoint));
        float loopTwo = 1.0 - smoothstep(0.045, 0.12,
                abs(ellipse(solarPoint, vec2(-0.69, -0.70), vec2(0.23, 0.15), 0.52) - 1.0));
        loopTwo *= smoothstep(0.96, 1.035, length(solarPoint));

        float diffraction = (exp(-abs(point.x) * 42.0) + exp(-abs(point.y) * 42.0))
                * exp(-outside * 5.0) * limbMask * 0.018;
        float plasma = prominences * 0.62 + (loopOne + loopTwo) * 0.46;
        float intensity = (glare * 0.105 + diffraction + plasma) * vertexColor.a * mix(0.76, 1.12, Exposure);
        if (intensity < 0.0007) {
            discard;
        }

        vec3 color = mix(vec3(1.0, 0.78, 0.42), vec3(1.0, 0.22, 0.025),
                clamp(plasma * 1.8, 0.0, 1.0));
        fragColor = vec4(color * vertexColor.rgb * intensity, intensity) * ColorModulator;
        return;
    }

    if (RenderMode > 0.5) {
        // Reconstruct the visible hemisphere so surface noise follows a sphere
        // instead of reading as a flat texture pasted onto a circle.
        float radius = sqrt(radiusSquared);
        vec2 edgeDirection = point / max(radius, 0.001);
        float edgeTurbulence = (valueNoise(edgeDirection * 7.0 + vec2(Time * 0.012, -Time * 0.009)) - 0.5) * 0.010;
        float apparentRadius = 0.992 + edgeTurbulence;
        float edge = 1.0 - smoothstep(apparentRadius - 0.022, apparentRadius, radius);
        if (edge < 0.002) {
            discard;
        }

        float normalizedRadiusSquared = radiusSquared / (apparentRadius * apparentRadius);
        float mu = sqrt(max(0.0, 1.0 - normalizedRadiusSquared));
        vec3 sphere = normalize(vec3(point / apparentRadius, mu));

        vec2 slowFlow = vec2(Time * 0.010, -Time * 0.007);
        float convection = fbm(sphere.xy * 7.5 + slowFlow);
        float granules = valueNoise(sphere.xy * 25.0 + vec2(convection * 3.2) - slowFlow * 1.8);
        float mottling = (convection - 0.50) * 0.22 + (granules - 0.50) * 0.15;

        // Faculae are most apparent near the limb, where hot magnetic regions
        // contrast against the darker photosphere.
        float faculaNoise = fbm(sphere.xy * 13.0 + vec2(19.0, 7.0) - slowFlow * 0.7);
        float faculae = smoothstep(0.60, 0.82, faculaNoise) * smoothstep(0.16, 0.72, 1.0 - mu);

        float rotation = sin(Time * 0.003) * 0.10;
        float spotOnePenumbra = 1.0 - smoothstep(0.66, 1.0,
                ellipse(point, vec2(-0.30 + rotation, 0.17), vec2(0.17, 0.095), -0.18));
        float spotOneUmbra = 1.0 - smoothstep(0.42, 0.70,
                ellipse(point, vec2(-0.30 + rotation, 0.17), vec2(0.17, 0.095), -0.18));
        float spotTwoPenumbra = 1.0 - smoothstep(0.64, 1.0,
                ellipse(point, vec2(0.40 + rotation * 0.6, -0.25), vec2(0.115, 0.066), 0.26));
        float spotTwoUmbra = 1.0 - smoothstep(0.40, 0.68,
                ellipse(point, vec2(0.40 + rotation * 0.6, -0.25), vec2(0.115, 0.066), 0.26));
        float sunspotPenumbra = max(spotOnePenumbra, spotTwoPenumbra);
        float sunspotUmbra = max(spotOneUmbra, spotTwoUmbra);

        float limbDarkening = mix(0.54, 1.0, pow(mu, 0.42));
        float intensity = limbDarkening * (0.91 + mottling + faculae * 0.22);
        intensity *= 1.0 - sunspotPenumbra * 0.34 - sunspotUmbra * 0.34;
        intensity *= edge * vertexColor.a;

        vec3 limbColor = vec3(1.0, 0.34, 0.035);
        vec3 centerColor = vec3(1.0, 0.92, 0.61);
        vec3 solarColor = mix(limbColor, centerColor, pow(mu, 0.38));
        solarColor = mix(solarColor, vec3(0.50, 0.075, 0.012), sunspotPenumbra * 0.64 + sunspotUmbra * 0.28);
        solarColor += vec3(0.09, 0.045, 0.005) * faculae;

        fragColor = vec4(solarColor * vertexColor.rgb * intensity, intensity) * ColorModulator;
        return;
    }

    // An airless sky retains a useful bright-star floor even while the player
    // is adapted to sunlit regolith. Dark adaptation progressively reveals the
    // much denser faint population instead of switching the whole field off.
    float visibilityThreshold = mix(0.62, 0.018, Exposure);
    float magnitudeVisibility = smoothstep(visibilityThreshold, min(1.0, visibilityThreshold + 0.10), vertexColor.a);
    float halo = 1.0 - smoothstep(0.10, 1.0, radiusSquared);
    float core = 1.0 - smoothstep(0.0, 0.30, radiusSquared);
    float intensity = (halo * 0.52 + core * 1.08) * vertexColor.a * magnitudeVisibility * mix(0.58, 1.12, Exposure);
    if (intensity < 0.003) {
        discard;
    }

    // space_star uses additive blending, so the source color carries intensity.
    fragColor = vec4(vertexColor.rgb * intensity, intensity) * ColorModulator;
}
