package pk.np.pasir_nowak_pawel.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Nazwa użytkownika jest wymagana")
    private String username;

    @Email(message = "Podaj poprawny adres e-mail")
    @NotBlank(message = "Adres e-mail jest wymagany")
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank(message = "Hasło nie może być puste")
    private String password;

    private String currency = "PLN";
}