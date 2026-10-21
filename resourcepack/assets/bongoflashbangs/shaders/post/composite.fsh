#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:globals.glsl>
#include <bongoflashbangs:flash_time.glsl>
uniform sampler2D SceneSampler;
uniform sampler2D FrozenSampler;
uniform sampler2D ClockSampler;
layout(std140) uniform FlashConfig {
    float WhiteHold;
    float WhiteEnd;
    float GhostEnd;
    float GhostStrength;
    float BlurPixels;
    float WhiteStrength;
};
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

vec3 frozenBlur(vec2 uv, vec2 d) {
    vec3 c = texture(FrozenSampler, uv).rgb * 0.24;
    c += texture(FrozenSampler, clamp(uv + vec2(d.x, 0), 0.0, 1.0)).rgb * 0.12;
    c += texture(FrozenSampler, clamp(uv - vec2(d.x, 0), 0.0, 1.0)).rgb * 0.12;
    c += texture(FrozenSampler, clamp(uv + vec2(0, d.y), 0.0, 1.0)).rgb * 0.12;
    c += texture(FrozenSampler, clamp(uv - vec2(0, d.y), 0.0, 1.0)).rgb * 0.12;
    c += texture(FrozenSampler, clamp(uv + d, 0.0, 1.0)).rgb * 0.07;
    c += texture(FrozenSampler, clamp(uv - d, 0.0, 1.0)).rgb * 0.07;
    c += texture(FrozenSampler, clamp(uv + vec2(d.x, -d.y), 0.0, 1.0)).rgb * 0.07;
    c += texture(FrozenSampler, clamp(uv + vec2(-d.x, d.y), 0.0, 1.0)).rgb * 0.07;
    return c;
}
void main() {
    vec4 scene = texture(SceneSampler, texCoord);
    float t = elapsedSeconds(GameTime, texelFetch(ClockSampler, ivec2(0), 0).rgb);
    // Fully transparent once finished, even if a lagging server hasn't removed the effect yet.
    if (t >= GhostEnd) { fragColor = scene; return; }
    float white = WhiteStrength * (1.0 - smoothstep(WhiteHold, WhiteEnd, t));
    float ghost = GhostStrength * (1.0 - smoothstep(WhiteHold, GhostEnd, t));
    vec2 pixel = 1.0 / vec2(textureSize(FrozenSampler, 0));
    vec3 frozen = frozenBlur(texCoord, pixel * BlurPixels * (1.0 - smoothstep(0.0, GhostEnd, t)));
    float luma = dot(frozen, vec3(0.2126, 0.7152, 0.0722));
    // A washed-out positive image: the burned scene remains still while the real camera moves.
    vec3 burned = mix(vec3(luma), frozen, 0.15);
    burned = mix(burned, vec3(1.0), 0.32);
    vec3 recovering = mix(scene.rgb, burned, ghost);
    fragColor = vec4(mix(recovering, vec3(1.0), white), scene.a);
}
