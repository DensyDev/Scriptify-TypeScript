package org.densy.scriptify.ts.swc.rhino.script;

import com.caoccao.javet.swc4j.Swc4j;
import com.caoccao.javet.swc4j.enums.Swc4jMediaType;
import com.caoccao.javet.swc4j.options.Swc4jTranspileOptions;
import com.caoccao.javet.swc4j.outputs.Swc4jTranspileOutput;
import org.densy.scriptify.api.exception.ScriptException;
import org.densy.scriptify.api.script.CompiledScript;
import org.densy.scriptify.api.script.Script;
import org.densy.scriptify.api.script.constant.ScriptConstant;
import org.densy.scriptify.api.script.constant.ScriptConstantManager;
import org.densy.scriptify.api.script.function.ScriptFunctionManager;
import org.densy.scriptify.api.script.function.definition.ScriptFunctionDefinition;
import org.densy.scriptify.api.script.security.ScriptSecurityManager;
import org.densy.scriptify.core.script.constant.StandardConstantManager;
import org.densy.scriptify.core.script.function.StandardFunctionManager;
import org.densy.scriptify.core.script.security.StandardSecurityManager;
import org.densy.scriptify.js.rhino.script.JsCompiledScript;
import org.densy.scriptify.js.rhino.script.JsFunction;
import org.densy.scriptify.js.rhino.script.JsSecurityClassAccessor;
import org.densy.scriptify.js.rhino.script.JsWrapFactory;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TsScript implements Script<Object> {

    private final ScriptSecurityManager securityManager = new StandardSecurityManager();
    private ScriptFunctionManager functionManager = new StandardFunctionManager();
    private ScriptConstantManager constantManager = new StandardConstantManager();
    private final List<String> extraScript = new ArrayList<>();
    private final Swc4j swc = new Swc4j();

    @Override
    public ScriptSecurityManager getSecurityManager() {
        return securityManager;
    }

    @Override
    public ScriptFunctionManager getFunctionManager() {
        return functionManager;
    }

    @Override
    public void setFunctionManager(ScriptFunctionManager functionManager) {
        this.functionManager = Objects.requireNonNull(functionManager, "functionManager cannot be null");
    }

    @Override
    public ScriptConstantManager getConstantManager() {
        return constantManager;
    }

    @Override
    public void setConstantManager(ScriptConstantManager constantManager) {
        this.constantManager = Objects.requireNonNull(constantManager, "constantManager cannot be null");
    }

    @Override
    public void addExtraScript(String script) {
        this.extraScript.add(script);
    }

    @Override
    public CompiledScript<Object> compile(String script) throws ScriptException {
        try {
            Context context = Context.enter();
            context.setWrapFactory(new JsWrapFactory());

            ScriptableObject scope = context.initStandardObjects();

            // If security mode is enabled, search all exclusions
            // and add the classes that were excluded to JsSecurityClassAccessor
            if (securityManager.getSecurityMode()) {
                context.setClassShutter(new JsSecurityClassAccessor(securityManager.getExcludes()));
            }

            for (ScriptFunctionDefinition definition : functionManager.getFunctions().values()) {
                scope.put(definition.getFunction().getName(), scope, new JsFunction(this, definition));
            }

            for (ScriptConstant constant : constantManager.getConstants().values()) {
                ScriptableObject.putConstProperty(scope, constant.getName(), constant.getValue());
            }

            // Building full script including extra script code
            StringBuilder fullScript = new StringBuilder();
            for (String extra : extraScript) {
                fullScript.append(extra).append("\n");
            }
            fullScript.append(script);

            // Transpile TypeScript to JavaScript and evaluate it
            Swc4jTranspileOptions options = new Swc4jTranspileOptions().setMediaType(Swc4jMediaType.TypeScript);
            Swc4jTranspileOutput output = swc.transpile(fullScript.toString(), options);
            var compiled = context.compileString(output.getCode(), "script", 1, null);
            return new JsCompiledScript(context, scope, compiled);
        } catch (Exception e) {
            throw new ScriptException(e);
        }
    }

    @Override
    public Object evalOneShot(String script) throws ScriptException {
        try (CompiledScript<Object> compiled = compile(script)) {
            return compiled.get();
        }
    }
}
