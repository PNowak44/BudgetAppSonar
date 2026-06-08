package pk.np.pasir_nowak_pawel.dto;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.*;

@Getter
@Setter
public class TransactionDTO {
    @NotNull(message = "Kwota nie może być pusta")
    @DecimalMin(value = "0.01", message = "Kwota musi być większa od 0")
    private Double amount;

    @NotNull(message = "Typ transakcji jest wymagany")
    @Pattern(regexp = "INCOME|EXPENSE", message = "Typ musi być wartością INCOME lub EXPENSE")
    private String type;

    @Size(max = 50, message = "Tagi nie mogą przekraczać 50 znaków")
    private String tags;

    @Size(max = 255, message = "Notatka może mieć maksymalnie 255 znaków")
    private String notes;
}