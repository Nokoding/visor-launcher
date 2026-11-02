package com.nokoding.deck;

/** Hand written damped spring. Pure Java (no Android imports) so the JVM tests can run it. */
final class Spring {
    interface Sched { void add(Spring s); }

    float value, velocity, target;
    float stiffness, damping;
    final float eps;
    boolean active;
    private final Sched sched;

    Spring(Sched sched, float stiffness, float zeta, float eps) {
        this.sched = sched;
        this.eps = eps;
        set(stiffness, zeta);
    }

    void set(float stiffness, float zeta) {
        this.stiffness = stiffness;
        this.damping = 2f * zeta * (float) Math.sqrt(stiffness);
    }

    void to(float t) {
        if (t != target || Math.abs(value - t) > eps || Math.abs(velocity) > eps) {
            target = t;
            if (sched != null) sched.add(this);
        }
    }

    void snap(float v) {
        value = v;
        target = v;
        velocity = 0f;
    }

    void kick(float v) {
        velocity += v;
        if (sched != null) sched.add(this);
    }

    /** Move value and target together (used to rebase the infinite wheel). */
    void shift(float d) {
        value -= d;
        target -= d;
    }

    /** @return true when at rest. */
    boolean step(float dt) {
        int n = (int) Math.ceil(dt / 0.008f);
        if (n < 1) n = 1;
        float h = dt / n;
        for (int i = 0; i < n; i++) {
            float a = -stiffness * (value - target) - damping * velocity;
            velocity += a * h;
            value += velocity * h;
        }
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            snap(target);
            return true;
        }
        if (Math.abs(value - target) < eps && Math.abs(velocity) < eps * 4f) {
            value = target;
            velocity = 0f;
            return true;
        }
        return false;
    }
}
