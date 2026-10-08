import java.time.LocalDate;

public class PrepItem {
    public int id;
    public String title;
    public String category;
    public String difficulty;
    public String status;
    public String targetDate;
    public String notes;
    public String completedAt;   // date the item was marked DONE (YYYY-MM-DD)

    private boolean hasDate() {
        return targetDate != null && !targetDate.isBlank();
    }

    // Target date is in the past and the item is not finished
    public boolean isOverdue() {
        return !"DONE".equals(status) && hasDate()
                && targetDate.compareTo(LocalDate.now().toString()) < 0;
    }

    // Target date is today and the item is not finished
    public boolean isDueToday() {
        return !"DONE".equals(status) && hasDate()
                && targetDate.equals(LocalDate.now().toString());
    }
}
