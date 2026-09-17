package com.texto.emailplatform.complaint;

public record ComplaintPolicyDecision(ComplaintPolicyAction action) {

    public static final ComplaintPolicyDecision NO_ACTION =
            new ComplaintPolicyDecision(ComplaintPolicyAction.NO_ACTION);
    public static final ComplaintPolicyDecision SUPPRESS =
            new ComplaintPolicyDecision(ComplaintPolicyAction.SUPPRESS);

    public boolean suppresses() {
        return action == ComplaintPolicyAction.SUPPRESS;
    }
}
