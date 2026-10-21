#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D SceneSampler;
uniform sampler2D HistorySampler;
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    vec4 previous = texture(HistorySampler, texCoord);
    // Copy the scene BEFORE the flash composite; once valid it stays fixed in screen space.
    fragColor = previous.a > 0.5 ? previous : vec4(texture(SceneSampler, texCoord).rgb, 1.0);
}
