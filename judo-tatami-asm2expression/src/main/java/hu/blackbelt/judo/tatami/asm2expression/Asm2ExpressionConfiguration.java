package hu.blackbelt.judo.tatami.asm2expression;

public class Asm2ExpressionConfiguration {

    private boolean resolveOnlyCurrentLambdaScope = true;

    public boolean isResolveOnlyCurrentLambdaScope() {
        return resolveOnlyCurrentLambdaScope;
    }

    public void setResolveOnlyCurrentLambdaScope(boolean resolveOnlyCurrentLambdaScope) {
        this.resolveOnlyCurrentLambdaScope = resolveOnlyCurrentLambdaScope;
    }

}
