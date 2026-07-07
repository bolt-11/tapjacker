package com.boltz.tapjacker;

public class OverlayConfig {
    public String text;
    public int widthDp;
    public int heightDp;
    public int opacityPercent;
    public int color;
    public boolean locked;

    public OverlayConfig(String text, int widthDp, int heightDp, int opacityPercent, int color, boolean locked) {
        this.text = text;
        this.widthDp = widthDp;
        this.heightDp = heightDp;
        this.opacityPercent = opacityPercent;
        this.color = color;
        this.locked = locked;
    }
}
