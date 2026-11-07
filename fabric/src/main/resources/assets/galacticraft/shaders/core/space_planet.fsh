#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform vec3 Direction;
uniform vec3 SunDirection;
uniform mat3 Orientation;
uniform vec3 Atmosphere;
uniform float Thickness;
uniform float DistanceRadius;
uniform float Rotation;
uniform float Style;
uniform float Opacity;
uniform float AmbientLight;
uniform float HorizonFade;
in vec3 skyDirection;
in vec3 cubeSurface;
flat in vec3 faceNormal;
out vec4 fragColor;

// Slab intersection with a cube, including parallel rays.
vec2 cubeIntersection(vec3 center, vec3 ray, float extent) {
    vec3 inverseRay = 1.0 / (mix(vec3(-1.0), vec3(1.0), step(vec3(0.0), ray)) * max(abs(ray), vec3(1e-6)));
    vec3 a = (center - vec3(extent)) * inverseRay;
    vec3 b = (center + vec3(extent)) * inverseRay;
    vec3 near = min(a, b), far = max(a, b);
    return vec2(max(near.x, max(near.y, near.z)), min(far.x, min(far.y, far.z)));
}

// Planet maps are cube nets: six square faces in a 4 x 3 cross, the sides in row 1 (+Z, +X, -Z, -X, i.e.
// 90 W, 0, 90 E, 180) with the north cap above +X and the south cap below it. Matches tools/planet_pixel_maps.js.
const float NET_COLUMNS = 4.0;

void cubeFace(vec3 n, vec3 p, out vec2 cell, out vec2 local) {
    vec3 right, down;
    if (abs(n.y) > 0.5) {
        right = vec3(0.0, 0.0, -1.0);
        down = vec3(sign(n.y), 0.0, 0.0);
        cell = vec2(1.0, n.y > 0.0 ? 0.0 : 2.0);
    } else {
        right = cross(vec3(0.0, 1.0, 0.0), n);
        down = vec3(0.0, -1.0, 0.0);
        cell = vec2(n.z > 0.5 ? 0.0 : n.x > 0.5 ? 1.0 : n.z < -0.5 ? 2.0 : 3.0, 1.0);
    }
    local = vec2(dot(p, right), dot(p, down)) * 0.5 + 0.5;
}

// Pixel-art sampling with a linear-filtered texture, aligned to one face of the net: hard texel edges when
// magnified, a one-pixel blend at each edge, plain bilinear when minified. Samples stay half a texel inside the
// face, so filtering never reaches a neighbouring cell; across a cube edge the derivative is huge, which falls back
// to the texel centre instead of drawing a seam.
vec4 faceTexture(sampler2D tex, vec2 cell, vec2 local, vec2 localWidth) {
    vec2 size = vec2(textureSize(tex, 0));
    float face = size.x / NET_COLUMNS;
    vec2 p = local * face;
    vec2 a = max(localWidth * face * 0.7, vec2(1e-4));
    vec2 f = fract(p);
    vec2 g = clamp(0.5 / a * f, 0.0, 0.5) + clamp(0.5 / a * (f - 1.0) + 0.5, 0.0, 0.5);
    vec2 texel = clamp(floor(p) + g, vec2(0.5), vec2(face - 0.5));
    return texture(tex, (cell * face + texel) / size);
}

float surfaceNoise(vec2 point) {
    return fract(sin(dot(floor(point), vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    float visibility = Opacity * mix(1.0, smoothstep(-0.015, 0.015, normalize(skyDirection).y), HorizonFade);
    bool sun = Style > 1.5;
    // Work in the body's own spinning, tilted frame, where the cube and its net are fixed and axis-aligned.
    mat3 toBody = transpose(Orientation);
    vec3 center = toBody * (Direction * DistanceRadius);
    vec3 ray = normalize(cubeSurface + center);
    vec3 sunDirection = toBody * SunDirection;
    vec3 point = cubeSurface;
    vec3 normal = faceNormal;
    float altitude = 0.0;
    if (sun) {
        vec2 hit = cubeIntersection(center, ray, 1.0);
        if (hit.x <= hit.y && hit.y > 0.0) {
            point = ray * (hit.x > 0.0 ? hit.x : hit.y) - center;
            vec3 axis = abs(point);
            normal = normalize(sign(point) * step(vec3(max(axis.x, max(axis.y, axis.z)) - 1e-4), axis));
        } else {
            // Find the smallest expanded cube hit by this ray so the corona follows the square silhouette.
            float low = 1.0, high = 2.2;
            for (int i = 0; i < 8; i++) {
                float middle = (low + high) * 0.5;
                vec2 expanded = cubeIntersection(center, ray, middle);
                if (expanded.x <= expanded.y && expanded.y > 0.0) high = middle;
                else low = middle;
            }
            altitude = high - 1.0;
        }
    }
    // Every body reads its cube net face by face, fixed to the faces like a block. Take screen derivatives
    // before discarding the outer glow.
    vec2 cell, local;
    cubeFace(normal, point, cell, local);
    vec2 localWidth = fwidth(local);
    // Clouds drift east around the four side faces, which form one continuous strip of the net.
    vec2 cloudCell = cell, cloudLocal = local;
    if (cell.y == 1.0) {
        float strip = mod(cell.x + local.x + Rotation * 0.14, NET_COLUMNS);
        cloudCell.x = floor(strip);
        cloudLocal.x = fract(strip);
    }
    if (sun && altitude > 0.0) {
        if (altitude > 1.0) discard;
        fragColor = vec4(vec3(1.0, 0.66, 0.28), exp(-altitude * 12.0) * 0.48 * visibility);
        return;
    }
    vec3 albedo = faceTexture(Sampler0, cell, local, localWidth).rgb;
    float facing = max(dot(normal, -ray), 0.0);
    float cameraAltitude = length(max(abs(center) - vec3(1.0), vec3(0.0)));
    float closeSurface = 1.0 - smoothstep(0.05, 1.0, cameraAltitude);
    // Detail stays fixed to the face, so the approach has parallax even inside a single map texel.
    vec2 surfacePoint = (cell + local) * 128.0;
    float detail = (surfaceNoise(surfacePoint) - 0.5) * 0.18
            + (surfaceNoise(surfacePoint * 4.0) - 0.5) * 0.09
            + (surfaceNoise(surfacePoint * 16.0) - 0.5) * 0.04;
    if (Style < 1.5) albedo = clamp(albedo * (1.0 + detail * closeSurface), 0.0, 1.0);
    if (Style > 1.5) {
        // Emissive photosphere with subtle face shading; the corona surrounds the solid cube.
        fragColor = vec4(mix(vec3(1.0, 0.80, 0.48), vec3(1.0, 0.96, 0.84), albedo.r)
                * (0.78 + 0.22 * sqrt(facing)), visibility);
        return;
    }
    // Light planets with a mostly spherical normal plus a little face shading: the day/night line sweeps smoothly
    // across the faces instead of snapping to a cube edge, and faces keep a gentle block-style shade difference.
    // The face part fades out toward every edge, so neighbouring faces meet on the same light with no step.
    vec3 alongFace = abs(point) * (vec3(1.0) - abs(normal));
    float fromEdge = 1.0 - max(alongFace.x, max(alongFace.y, alongFace.z));
    vec3 shadeNormal = normalize(normalize(point) + 0.35 * smoothstep(0.0, 0.35, fromEdge) * normal);
    float light = dot(shadeNormal, sunDirection);
    float day = smoothstep(-0.12, 0.12, light);
    vec3 color = albedo * (AmbientLight + 1.12 * pow(max(light, 0.0), 0.72));
    if (Style > 0.5) {
        // An independently moving cloud shell and warm city lights on Earth's night side.
        float clouds = smoothstep(0.07, 0.78, faceTexture(Sampler1, cloudCell, cloudLocal, localWidth).r);
        float cloudLight = max(dot(shadeNormal, sunDirection), 0.0);
        color = mix(color, vec3(1.0, 0.985, 0.96) * (0.012 + 1.05 * pow(cloudLight, 0.65)), clouds * 0.9);
        color += faceTexture(Sampler2, cell, local, localWidth).rgb * (1.0 - day) * (1.0 - clouds * 0.85) * 1.25;
        float ocean = smoothstep(0.03, 0.18, albedo.b - max(albedo.r, albedo.g));
        vec3 halfVector = normalize(sunDirection - ray);
        color += vec3(0.85, 0.91, 1.0) * pow(max(dot(shadeNormal, halfVector), 0.0), 90.0)
                * ocean * day * (1.0 - clouds) * 0.45;
    }
    vec3 edges = abs(cubeSurface) * (vec3(1.0) - abs(normal));
    float rim = pow(max(edges.x, max(edges.y, edges.z)), 12.0) * day;
    color = mix(color, Atmosphere * 0.9, rim * min(0.8, Thickness * 22.0));
    // Keep a subtle twilight tint on cube edges without rounding the silhouette.
    color += Atmosphere * exp(-abs(light) * 32.0) * (1.0 - facing) * Thickness * 2.0;
    if (Thickness > 0.0) {
        // Airless destinations keep a clear surface approach; atmospheric worlds pass through a moving haze.
        float air = closeSurface * min(1.0, Thickness * 45.0);
        float cloud = faceTexture(Sampler1, cloudCell, fract(cloudLocal + vec2(0.0, Rotation * 0.03)), localWidth).r;
        float veil = air * (0.12 + cloud * 0.28 * smoothstep(0.006, 0.018, Thickness));
        vec3 haze = mix(Atmosphere, vec3(0.9), 0.45) * (0.04 + day * 0.8);
        color = mix(color, haze, veil);
    }
    fragColor = vec4(color, visibility);
}