package com.mimicer.entity.scene;

/** Tiempos (en ticks) de las escenas de alimentación y extracción; los comparten servidor y cliente. */
public final class SceneTiming {
    /** Alimentación: tiempo hasta agacharse del todo, duración de cada mordisco y momento de arrancar la parte. */
    public static final int FEED_KNEEL = 36;
    public static final int FEED_CYCLE = 56;
    public static final int FEED_BITE_AT = 22;

    /** Extracción de la cama: agacharse y mirar, momento de agarrar y momento de terminar de sacar a la víctima. */
    public enum Kind {
        MIMIC(18, 48, 108),
        VOID(24, 64, 134);

        public final int kneel;
        public final int grab;
        public final int end;

        Kind(int kneel, int grab, int end) {
            this.kneel = kneel;
            this.grab = grab;
            this.end = end;
        }
    }

    private SceneTiming() {}
}
