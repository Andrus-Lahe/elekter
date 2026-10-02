import java.time.LocalDate;

void main() {
    String deliveryDate = LocalDate.now().plusDays(1).toString();
    System.out.println("Delivery date: " + deliveryDate);
}
