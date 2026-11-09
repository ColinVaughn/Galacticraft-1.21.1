#version 150

in vec3 Position;
in vec4 Color;
uniform mat4 ViewMat;
uniform mat3 Orientation;
uniform vec3 Direction;
uniform float DistanceRadius;
uniform mat4 ProjMat;
out vec3 cubeSurface;
out vec3 skyDirection;
flat out vec3 faceNormal;

void main() {
    gl_Position = ProjMat * ViewMat * vec4((Orientation * Position + Direction * DistanceRadius) * (100.0 / DistanceRadius), 1.0);
    skyDirection = Orientation * Position + Direction * DistanceRadius;
    cubeSurface = Position;
    faceNormal = Color.rgb * (Color.a * 2.0 - 1.0);
}