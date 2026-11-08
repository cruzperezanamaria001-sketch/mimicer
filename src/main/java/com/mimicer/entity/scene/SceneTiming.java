package com.mimicer.entity.scene;

/** Tiempos (en ticks) de las escenas de alimentación, camas y armarios; los comparten servidor y cliente. */
public final class SceneTiming {
    /** Alimentación: tiempo hasta agacharse del todo, duración de cada mordisco y momento de arrancar la parte. */
    public static final int FEED_KNEEL = 36;
    public static final int FEED_CYCLE = 56;
    public static final int FEED_BITE_AT = 22;

    /** Modos de la escena de extracción (sincronizados): cama, armario revisado (aún sin saber) y armario con víctima. */
    public static final int MODE_BED = 0, MODE_CLOSET_CHECK = 1, MODE_CLOSET_EXTRACT = 2;

    /**
     * Cama: agacharse y mirar ({@code kneel}), agarrar ({@code grab}) y terminar de sacar ({@code end}).
     * Armario: empezar a abrir ({@code cOpen}), duración de la apertura, agarrar ({@code cGrab}), fin de la
     * extracción ({@code cEnd}) y fin cuando está vacío ({@code cEmptyEnd}).
     */
    public enum Kind {
        MIMIC(18, 48, 108, 26, 14, 52, 104, 84),
        VOID(24, 64, 134, 36, 30, 76, 134, 112),
        REAPER(20, 50, 100, 24, 10, 46, 98, 78);

        public final int kneel;
        public final int grab;
        public final int end;
        public final int cOpen;
        public final int cOpenTicks;
        public final int cGrab;
        public final int cEnd;
        public final int cEmptyEnd;

        Kind(int kneel, int grab, int end, int cOpen, int cOpenTicks, int cGrab, int cEnd, int cEmptyEnd) {
            this.kneel = kneel;
            this.grab = grab;
            this.end = end;
            this.cOpen = cOpen;
            this.cOpenTicks = cOpenTicks;
            this.cGrab = cGrab;
            this.cEnd = cEnd;
            this.cEmptyEnd = cEmptyEnd;
        }
    }

    private SceneTiming() {}
}
