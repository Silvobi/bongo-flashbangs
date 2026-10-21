package pl.bongo.flashbangs;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.resources.Identifier;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import org.lwjgl.util.shaderc.ShadercIncludeResolve;
import org.lwjgl.util.shaderc.ShadercIncludeResultRelease;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.util.shaderc.Shaderc.*;

/** Runs real GLSL on an invisible OpenGL framebuffer; no game installation is changed. */
public final class EffectHarness {
    private static Path pack;
    private static final Map<String, Integer> programs = new HashMap<>();
    private static final String VERTEX = "#version 330\n#extension GL_ARB_separate_shader_objects : require\nlayout(location=0) out vec2 texCoord;\n"
        + "void main(){vec2 uv=vec2((gl_VertexID<<1)&2,gl_VertexID&2);"
        + "texCoord=uv;gl_Position=vec4(uv*2.0-1.0,0,1);}";
    private static int fbo, globals;
    private static final String GLOBALS = "layout(std140) uniform Globals { ivec3 CameraBlockPos; float GlintAlpha;"
        + " vec3 CameraOffset; float GameTime; vec2 ScreenSize; int MenuBlurRadius; int UseRgss; };";

    public static void main(String[] args) throws Exception {
        pack = Path.of(args[0]);
        Map<String, float[]> profiles = new LinkedHashMap<>();
        for (var strength : FlashStrength.values()) {
            com.google.gson.JsonElement primary = null;
            for (boolean alternate : new boolean[]{false, true}) {
                String id = strength.effect(alternate).getPath();
                var json = JsonParser.parseString(Files.readString(pack.resolve("assets/bongoflashbangs/post_effect/" + id + ".json")));
                var config = PostChainConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
                check(config.passes().size() == 6, id + " Minecraft codec / pass count");
                if (alternate) check(primary.equals(json), id + " matches primary chain");
                else primary = json;
                var uniforms = json.getAsJsonObject().getAsJsonArray("passes").get(4).getAsJsonObject()
                    .getAsJsonObject("uniforms").getAsJsonArray("FlashConfig");
                String[] names = {"WhiteHold", "WhiteEnd", "GhostEnd", "GhostStrength", "BlurPixels", "WhiteStrength"};
                check(uniforms.size() == names.length, id + " uniform count");
                float[] values = new float[8]; // std140 rounds the six floats to 32 bytes.
                for (int i = 0; i < names.length; i++) {
                    var uniform = uniforms.get(i).getAsJsonObject();
                    check(names[i].equals(uniform.get("name").getAsString()), id + " UBO layout");
                    values[i] = uniform.get("value").getAsFloat();
                }
                near(values[2], strength.durationTicks() / 20f, id + " pack and server durations agree");
                profiles.put(id, values);
            }
        }
        // Compile untouched pack files with ShaderC and the Minecraft include-result format.
        // Expanding directives before compilation used to conceal obsolete #moj_import syntax.
        try (PackShaderSource includes = new PackShaderSource();
             ShadercIncludeResolve resolve = ShadercIncludeResolve.create((user, requested, kind, from, depth) ->
                 includes.getInclude(Identifier.parse(MemoryUtil.memUTF8(requested))).includeResultPtr());
             ShadercIncludeResultRelease release = ShadercIncludeResultRelease.create((user, result) -> {})) {
            long compiler = shaderc_compiler_initialize();
            for (boolean zeroToOne : new boolean[]{false, true}) {
                long options = shaderc_compile_options_initialize();
                shaderc_compile_options_set_auto_bind_uniforms(options, true);
                shaderc_compile_options_set_auto_map_locations(options, true);
                shaderc_compile_options_set_include_callbacks(options, resolve, release, 0);
                shaderc_compile_options_add_macro_definition(options, "RENDERPEARL_DEPTH_IS_ZERO_TO_ONE", zeroToOne ? "1" : "0");
                try {
                    for (String name : new String[]{"capture", "clock", "copy", "composite"}) {
                        String raw = Files.readString(pack.resolve("assets/bongoflashbangs/shaders/post/" + name + ".fsh"));
                        long result = shaderc_compile_into_spv(compiler, raw, shaderc_fragment_shader,
                            "bongoflashbangs:post/" + name, "main", options);
                        try { check(shaderc_result_get_compilation_status(result) == shaderc_compilation_status_success,
                            "ShaderC " + name + ": " + shaderc_result_get_error_message(result)); }
                        finally { shaderc_result_release(result); }
                    }
                    String bad = Files.readString(pack.resolve("assets/bongoflashbangs/shaders/post/clock.fsh"))
                        .replace("#include", "#moj_import");
                    long result = shaderc_compile_into_spv(compiler, bad, shaderc_fragment_shader,
                        "legacy-import-regression", "main", options);
                    try { check(shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success,
                        "regression: obsolete import directives must fail"); }
                    finally { shaderc_result_release(result); }
                } finally { shaderc_compile_options_release(options); }
            }
            shaderc_compiler_release(compiler);
        }
        check(GLFW.glfwInit(), "GLFW initialization");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(32, 32, "BongoFlashbangs verification", 0, 0);
        check(window != 0, "hidden OpenGL context");
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            glBindVertexArray(glGenVertexArrays());
            fbo = glGenFramebuffers();
            globals = glGenBuffers();
            glBindBuffer(GL_UNIFORM_BUFFER, globals);
            glBufferData(GL_UNIFORM_BUFFER, 48, GL_DYNAMIC_DRAW);
            glBindBufferBase(GL_UNIFORM_BUFFER, 0, globals);
            for (String name : new String[]{"capture", "clock", "copy", "composite"}) programs.put(name, program(source(name)));
            int config = glGenBuffers();
            glBindBuffer(GL_UNIFORM_BUFFER, config);
            glBufferData(GL_UNIFORM_BUFFER, 32, GL_DYNAMIC_DRAW);
            glBindBufferBase(GL_UNIFORM_BUFFER, 1, config);
            int block = glGetUniformBlockIndex(programs.get("composite"), "FlashConfig");
            glUniformBlockBinding(programs.get("composite"), block, 1);
            int original = texture(0.8f, 0.2f, 0.1f, 1), moved = texture(0.1f, 0.3f, 0.8f, 1);
            for (var entry : profiles.entrySet()) {
                glBindBuffer(GL_UNIFORM_BUFFER, config);
                glBufferSubData(GL_UNIFORM_BUFFER, 0, entry.getValue());
                verifyProfile(entry.getKey(), entry.getValue(), original, moved);
            }
            check(glGetError() == GL_NO_ERROR, "OpenGL error state");
            System.out.println("PASS: all six chains through Minecraft codecs, raw ShaderC compilation (both depth conventions), legacy import rejection, GPU weak/medium/full timings, partial/full white, frozen afterimages, recovery, retrigger and timer wrap.");
        } finally { GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
    }
    private static void verifyProfile(String id, float[] config, int original, int moved) {
        int history = texture(0,0,0,0), frozen = texture(0,0,0,0);
        int clock = texture(0,0,0,0), clockNext = texture(0,0,0,0), output = texture(0,0,0,0);
        float start = 0.37f;
        time(start);
        draw("capture", frozen, new String[]{"Scene", "History"}, original, history);
        draw("copy", history, new String[]{"In"}, frozen);
        draw("clock", clockNext, new String[]{"Clock"}, clock);
        draw("copy", clock, new String[]{"In"}, clockNext);
        draw("composite", output, new String[]{"Scene", "Frozen", "Clock"}, original, frozen, clock);
        boolean weak = id.startsWith("flash_weak"), medium = id.startsWith("flash_medium");
        if (weak) check(pixel(output)[0] < 0.95f && pixel(output)[2] > 0.25f && pixel(output)[2] < 0.65f,
            id + " initial flash lightens without full blindness");
        else check(pixel(output)[0] > 0.99f && pixel(output)[2] > 0.99f, id + " initial full white");

        if (!weak) {
            time(start + (medium ? 0.1f : 1.99f) / 1200);
            draw("composite", output, new String[]{"Scene", "Frozen", "Clock"}, moved, frozen, clock);
            check(pixel(output)[0] > 0.99f && pixel(output)[2] > 0.99f, id + " white hold");
        }
        float middleSeconds = weak ? 0.25f : medium ? 0.6f : 2.5f;
        time(start + middleSeconds / 1200);
        draw("capture", frozen, new String[]{"Scene", "History"}, moved, history);
        near(pixel(frozen)[0], 0.8f, id + " history survives camera movement");
        draw("clock", clockNext, new String[]{"Clock"}, clock);
        draw("copy", clock, new String[]{"In"}, clockNext);
        draw("composite", output, new String[]{"Scene", "Frozen", "Clock"}, moved, frozen, clock);
        float[] middle = pixel(output);
        check(middle[0] > 0.13f && middle[0] < 0.95f, id + " partial recovery");
        // A different frozen frame must change the result, demonstrating an actual afterimage.
        draw("composite", output, new String[]{"Scene", "Frozen", "Clock"}, moved, moved, clock);
        check(Math.abs(middle[0] - pixel(output)[0]) > 0.005f, id + " frozen scene contributes to visible afterimage");

        time(start + (config[2] + 0.02f) / 1200);
        draw("composite", output, new String[]{"Scene", "Frozen", "Clock"}, moved, frozen, clock);
        near(pixel(output)[0], 0.1f, id + " recovery red");
        near(pixel(output)[2], 0.8f, id + " recovery blue");
        int freshHistory = texture(0,0,0,0), freshClock = texture(0,0,0,0);
        time(0.999f);
        draw("capture", frozen, new String[]{"Scene", "History"}, moved, freshHistory);
        near(pixel(frozen)[2], 0.8f, id + " retrigger captures new frame");
        draw("clock", clockNext, new String[]{"Clock"}, freshClock);
        draw("copy", freshClock, new String[]{"In"}, clockNext);
        time(0.0012f); // 2.64 seconds across the 20-minute clock wrap.
        draw("composite", output, new String[]{"Scene", "Frozen", "Clock"}, original, frozen, freshClock);
        if (weak || medium) near(pixel(output)[0], 0.8f, id + " recovered across time wrap");
        else check(pixel(output)[0] > 0.2f && pixel(output)[0] < 0.85f, id + " fade survives time wrap");
        glDeleteTextures(new int[]{history, frozen, clock, clockNext, output, freshHistory, freshClock});
        System.out.println("GPU_PASS: " + id);
    }
    private static String source(String name) throws Exception {
        return Files.readString(pack.resolve("assets/bongoflashbangs/shaders/post/" + name + ".fsh"))
            .replace("#include <minecraft:globals.glsl>", GLOBALS)
            .replace("#include <bongoflashbangs:flash_time.glsl>", Files.readString(pack.resolve("assets/bongoflashbangs/shaders/include/flash_time.glsl")));
    }
    private static final class PackShaderSource implements ShaderSource {
        private final Map<Identifier, CachedIncludeSource> cache = new HashMap<>();
        @Override public String getShader(Identifier id, ShaderType type) { return null; }
        @Override public CachedIncludeSource getInclude(Identifier id) {
            return cache.computeIfAbsent(id, key -> {
                try {
                    String text;
                    if (key.toString().equals("minecraft:globals.glsl")) text = GLOBALS;
                    else {
                        Path path = pack.resolve("assets/" + key.getNamespace() + "/shaders/include/" + key.getPath());
                        if (!Files.isRegularFile(path)) return null;
                        text = Files.readString(path);
                    }
                    return CachedIncludeSource.create(key, text);
                } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            });
        }
        @Override public void close() { cache.values().forEach(CachedIncludeSource::close); }
    }
    private static void time(float t) {
        ByteBuffer b = ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder()); b.putFloat(28, t);
        glBindBuffer(GL_UNIFORM_BUFFER, globals); glBufferSubData(GL_UNIFORM_BUFFER, 0, b);
    }
    private static int texture(float r,float g,float b,float a) {
        int id=glGenTextures(); glBindTexture(GL_TEXTURE_2D,id);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,1,1,0,GL_RGBA,GL_FLOAT,new float[]{r,g,b,a});
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE); return id;
    }
    private static void draw(String name,int output,String[] names,int... inputs) {
        int p=programs.get(name); glUseProgram(p); glBindFramebuffer(GL_FRAMEBUFFER,fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,output,0);
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"framebuffer complete"); glViewport(0,0,1,1);
        for(int i=0;i<inputs.length;i++){glActiveTexture(GL_TEXTURE0+i);glBindTexture(GL_TEXTURE_2D,inputs[i]);glUniform1i(glGetUniformLocation(p,names[i]+"Sampler"),i);}
        glDrawArrays(GL_TRIANGLES,0,3);
    }
    private static float[] pixel(int texture) {
        glBindTexture(GL_TEXTURE_2D,texture); float[] value=new float[4]; glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_FLOAT,value); return value;
    }
    private static int program(String fragment) {
        int p=glCreateProgram(); glAttachShader(p,shader(GL_VERTEX_SHADER,VERTEX)); glAttachShader(p,shader(GL_FRAGMENT_SHADER,fragment));
        glLinkProgram(p); check(glGetProgrami(p,GL_LINK_STATUS)!=0,glGetProgramInfoLog(p));
        int block=glGetUniformBlockIndex(p,"Globals"); if(block!=GL_INVALID_INDEX) glUniformBlockBinding(p,block,0); return p;
    }
    private static int shader(int kind,String source) { int s=glCreateShader(kind);glShaderSource(s,source);glCompileShader(s);check(glGetShaderi(s,GL_COMPILE_STATUS)!=0,glGetShaderInfoLog(s));return s; }
    private static void near(float a,float b,String label) {check(Math.abs(a-b)<0.01f,label+": "+a);}
    private static void check(boolean result,String label) {if(!result)throw new AssertionError(label);}
}
