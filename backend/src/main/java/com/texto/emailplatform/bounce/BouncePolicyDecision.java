package com.texto.emailplatform.bounce;

/**
 * Policy decision for one correlated DSN recipient block.
 */
public record BouncePolicyDecision(BouncePolicyAction action) {

    public static final BouncePolicyDecision NO_ACTION = new BouncePolicyDecision(BouncePolicyAction.NO_ACTION);
    public static final BouncePolicyDecision DEFER = new BouncePolicyDecision(BouncePolicyAction.DEFER);
    public static final BouncePolicyDecision BOUNCE = new BouncePolicyDecision(BouncePolicyAction.BOUNCE);
    public static final BouncePolicyDecision SUPPRESS = new BouncePolicyDecision(BouncePolicyAction.SUPPRESS);

    public boolean suppresses() {
        return action == BouncePolicyAction.SUPPRESS;
    }

    public boolean bouncesRecipient() {
        return action == BouncePolicyAction.SUPPRESS || action == BouncePolicyAction.BOUNCE;
    }

    public boolean defersRecipient() {
        return action == BouncePolicyAction.DEFER;
    }
}
