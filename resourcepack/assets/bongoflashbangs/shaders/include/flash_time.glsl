#ifndef BONGO_FLASH_TIME
#define BONGO_FLASH_TIME
// Internal targets use RGBA8_UNORM, so a raw float timestamp would be clamped/quantized.
// Store the normalized 20-minute GameTime clock in three exact byte channels.
vec3 encodeTime(float time) {
    uint value = uint(fract(time) * 16777215.0 + 0.5);
    return vec3(float((value >> 16u) & 255u), float((value >> 8u) & 255u),
                float(value & 255u)) / 255.0;
}
float decodeTime(vec3 rgb) {
    uvec3 bytes = uvec3(rgb * 255.0 + 0.5);
    return float((bytes.r << 16u) | (bytes.g << 8u) | bytes.b) / 16777215.0;
}
float elapsedSeconds(float time, vec3 start) {
    float delta = fract(time) - decodeTime(start);
    // A tiny negative value can come from byte packing; a large one is the clock wrap.
    return (delta < -0.5 ? delta + 1.0 : max(delta, 0.0)) * 1200.0;
}
#endif
