#version 150

uniform sampler2D DepthSampler;
uniform sampler2D CloudSampler;
uniform sampler2D CloudDepthSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    gl_FragDepth = depth;
    // Most pixels contain only clouds and sky after the cube mesh is removed.
    if (depth >= 0.9999999 && texture(CloudDepthSampler, texCoord).r >= 0.9999999) {
        vec4 cloud = texture(CloudSampler, texCoord);
        fragColor = cloud;
        return;
    }
    vec2 size = vec2(textureSize(CloudSampler, 0));
    vec2 point = texCoord * size - 0.5;
    vec2 base = floor(point), f = fract(point);
    vec4 clouds = vec4(0.0);
    float total = 0.0, best = 0.0;
    for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
        vec2 uv = (clamp(base + vec2(x, y), vec2(0), size - 1.0) + 0.5) / size;
        float lowDepth = texture(CloudDepthSampler, uv).r;
        float agreement = 1.0 / (1.0 + abs(depth - lowDepth) * 200.0 / max(1.0 - depth, 1e-6));
        float weight = (x == 0 ? 1.0 - f.x : f.x) * (y == 0 ? 1.0 - f.y : f.y) * agreement;
        clouds += texture(CloudSampler, uv) * weight;
        total += weight;
        best = max(best, agreement);
    }
    clouds = best > 0.05 ? clouds / max(total, 1e-6) : vec4(0.0);
    fragColor = clouds;
}