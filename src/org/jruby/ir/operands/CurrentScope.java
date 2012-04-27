package org.jruby.ir.operands;

import java.util.List;
import org.jruby.ir.IRScope;
import org.jruby.ir.targets.JVM;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.ir.transformations.inlining.InlinerInfo;

public class CurrentScope extends Operand {
    private final IRScope scope;

    public CurrentScope(IRScope scope) {
        this.scope = scope;
    }
    
    @Override
    public void addUsedVariables(List<Variable> l) {
        /* Nothing to do */
    }    

    @Override
    public Operand cloneForInlining(InlinerInfo ii) {
        return this;
    }

    public IRScope getScope() {
        return scope;
    }

    @Override
    public boolean canCopyPropagate() {
        return true;
    }

    @Override
    public String toString() {
        return "scope<" + scope.getName() + ">";
    }

    @Override
    public Object retrieve(ThreadContext context, IRubyObject self, DynamicScope currDynScope, Object[] temp) {
        return scope.getStaticScope();
    }

    @Override
    public void compile(JVM jvm) {
        jvm.method().adapter.aload(1);
    }
}
