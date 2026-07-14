#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 ScreenSize;
uniform float PointScale;
uniform float RenderMode;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    vec4 clipPosition = ProjMat * ModelViewMat * vec4(Position, 1.0);

    // Background stars provide four copies of the same world-space center.
    // Expand those corners in clip space so distance and resolution cannot
    // reduce a star below a visible number of pixels. The Sun keeps using its
    // regular world-space quad with PointScale disabled.
    if (RenderMode < 0.5 && PointScale > 0.5) {
        vec2 corner = UV0 * 2.0 - 1.0;
        float anchor = smoothstep(0.34, 1.0, Color.a);
        // Deliberately favor legibility over strict angular accuracy. At a
        // normal GUI scale this gives faint stars a roughly 3 px disc and the
        // brightest anchors a 6-7 px disc before their soft halo is applied.
        float pixelRadius = mix(1.45, 3.35, anchor);
        clipPosition.xy += corner * pixelRadius * (2.0 / ScreenSize) * clipPosition.w;
    }

    gl_Position = clipPosition;
    texCoord0 = UV0;
    vertexColor = Color;
}
