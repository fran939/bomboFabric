package me.bombo.bomboaddons.kuudra.pearls;

public class Supply {
   private final SupplySpot spot;
   private SupplyStatus status;
   private float[] progressColor;
   private int progressPercent = 0;

   public Supply(SupplySpot spot) {
      this.status = SupplyStatus.NOTHING;
      this.progressColor = new float[]{1.0F, 0.0F};
      this.progressPercent = 0;
      this.spot = spot;
   }

   public SupplySpot getSpot() {
      return this.spot;
   }

   public SupplyStatus getStatus() {
      return this.status;
   }

   public void setStatus(SupplyStatus status) {
      this.status = status;
   }

   public float[] getProgressColor() {
      return this.progressColor;
   }

   public void setProgressColor(float[] progressColor) {
      this.progressColor = progressColor;
   }

   public int getProgress() {
      return this.progressPercent;
   }

   public void setProgress(int percent) {
      this.progressPercent = percent;
   }

   public void reset() {
      this.status = SupplyStatus.NOTHING;
      this.progressColor = new float[]{1.0F, 0.0F};
      this.progressPercent = 0;
   }
}
