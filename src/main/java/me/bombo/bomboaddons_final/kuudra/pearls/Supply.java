package me.bombo.bomboaddons_final.kuudra.pearls;

public class Supply {
    private final SupplySpot spot;
    private SupplyStatus status = SupplyStatus.NOTHING;
    private float[] progressColor = new float[] {1.0f, 0.0f};

    public Supply(SupplySpot spot) {
        this.spot = spot;
    }

    public SupplySpot getSpot() { return spot; }
    public SupplyStatus getStatus() { return status; }
    public void setStatus(SupplyStatus status) { this.status = status; }
    public float[] getProgressColor() { return progressColor; }
    public void setProgressColor(float[] progressColor) { this.progressColor = progressColor; }
    
    public void reset() {
        status = SupplyStatus.NOTHING;
        progressColor = new float[] {1.0f, 0.0f};
    }
}
