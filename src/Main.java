import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

void main() {
    LocalDate tomorrow = LocalDate.now().plusDays(1);
    String deliveryDate = tomorrow.format(DateTimeFormatter.ISO_LOCAL_DATE);
    System.out.println("Delivery date: " + deliveryDate);
}
