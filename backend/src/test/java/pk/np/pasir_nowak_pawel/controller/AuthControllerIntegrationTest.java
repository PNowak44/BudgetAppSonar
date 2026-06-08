package pk.np.pasir_nowak_pawel.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import pk.np.pasir_nowak_pawel.dto.LoginDTO;
import pk.np.pasir_nowak_pawel.dto.UserDTO;
import pk.np.pasir_nowak_pawel.repository.UserRepository;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testy integracyjne dla AuthController.
 * Sprawdzają funkcjonalność rejestracji i logowania użytkowników.
 * <p>
 * Klasa używa adnotacji {@code @Transactional}, więc każdy test jest wykonywany w transakcji
 * i po zakończeniu następuje rollback, co zapewnia czystość bazy danych między testami.
 * <p>
 * Testy używają H2 in-memory database (profil "test") - izolowane od bazy produkcyjnej MySQL.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    private static final String TEST_USERNAME = "test_user_integration";
    private static final String TEST_PASSWORD = "SecurePassword123";

    @BeforeEach
    void setUp() {
        // Inicjalizuje MockMvc
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        
        // Wyczyść wszystkich użytkowników przed każdym testem.
        // @Transactional zapewnia rollback po teście, ale musimy wyczyścić dane z poprzednich uruchomień.
        userRepository.deleteAll();
    }

    // Helper method do generowania unikalnego emaila
    private String generateUniqueEmail() {
        return "test_" + UUID.randomUUID().toString().substring(0, 8) + "@pk.pl";
    }

    @Test
    @Order(1)
    @DisplayName("Powinien zarejestrować nowego użytkownika z poprawnymi danymi")
    void shouldRegisterNewUser() throws Exception {
        String email = generateUniqueEmail();
        
        UserDTO userDTO = new UserDTO();
        userDTO.setUsername(TEST_USERNAME);
        userDTO.setEmail(email);
        userDTO.setPassword(TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userDTO)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.username").value(TEST_USERNAME))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").exists()) // password hash
                .andExpect(jsonPath("$.password").value(not(TEST_PASSWORD))); // hasło jest zahashowane
    }

    @Test
    @Order(2)
    @DisplayName("Powinien zalogować użytkownika i zwrócić token JWT")
    void shouldLoginAndReturnJwtToken() throws Exception {
        String email = generateUniqueEmail();
        
        // Najpierw zarejestruj użytkownika
        UserDTO userDTO = new UserDTO();
        userDTO.setUsername(TEST_USERNAME);
        userDTO.setEmail(email);
        userDTO.setPassword(TEST_PASSWORD);
        
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userDTO)));

        // Teraz zaloguj
        LoginDTO loginDto = new LoginDTO();
        loginDto.setEmail(email);
        loginDto.setPassword(TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.token").value(matchesPattern("^[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_.+/=]*$"))); // JWT format
    }

    @Test
    @Order(3)
    @DisplayName("Powinien zwrócić 401 przy logowaniu z nieprawidłowym hasłem")
    void shouldReturn401WhenLoginWithWrongPassword() throws Exception {
        String email = generateUniqueEmail();
        
        // Najpierw zarejestruj użytkownika
        UserDTO userDTO = new UserDTO();
        userDTO.setUsername(TEST_USERNAME);
        userDTO.setEmail(email);
        userDTO.setPassword(TEST_PASSWORD);
        
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userDTO)));

        // Próba logowania z błędnym hasłem
        LoginDTO loginDto = new LoginDTO();
        loginDto.setEmail(email);
        loginDto.setPassword("WrongPassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    @DisplayName("Powinien zwrócić 401 przy logowaniu nieistniejącego użytkownika")
    void shouldReturn401WhenLoginNonExistentUser() throws Exception {
        LoginDTO loginDto = new LoginDTO();
        loginDto.setEmail("nonexistent@example.com");
        loginDto.setPassword("AnyPassword123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    @DisplayName("Powinien zwrócić 400 przy rejestracji z nieprawidłowym emailem")
    void shouldReturn400WhenRegisterWithInvalidEmail() throws Exception {
        UserDTO userDTO = new UserDTO();
        userDTO.setUsername("testuser");
        userDTO.setEmail("invalid-email"); // Brak @
        userDTO.setPassword("Password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userDTO)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    @DisplayName("Powinien zwrócić 400 przy rejestracji z pustymi polami")
    void shouldReturn400WhenRegisterWithEmptyFields() throws Exception {
        UserDTO userDTO = new UserDTO();
        userDTO.setUsername("");
        userDTO.setEmail("test@example.com");
        userDTO.setPassword("");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userDTO)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    @DisplayName("Powinien zwrócić 400 przy logowaniu z pustym emailem")
    void shouldReturn400WhenLoginWithEmptyEmail() throws Exception {
        LoginDTO loginDto = new LoginDTO();
        loginDto.setEmail("");
        loginDto.setPassword("Password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    @DisplayName("Hasło powinno być zahashowane w bazie danych (BCrypt)")
    void passwordShouldBeHashedInDatabase() throws Exception {
        String email = generateUniqueEmail();
        
        UserDTO userDTO = new UserDTO();
        userDTO.setUsername(TEST_USERNAME + "_hash");
        userDTO.setEmail(email);
        userDTO.setPassword(TEST_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userDTO)))
                .andExpect(status().isOk())
                .andReturn();

        String jsonResponse = result.getResponse().getContentAsString();
        
        // Sprawdź, czy hasło w odpowiedzi nie jest plain text
        assert !jsonResponse.contains(TEST_PASSWORD);
        
        // Sprawdź, czy hasło zaczyna się od prefiksu BCrypt
        assert jsonResponse.contains("$2a$") || jsonResponse.contains("$2b$");
    }

    @Test
    @Order(9)
    @DisplayName("Token JWT powinien zawierać prawidłową strukturę")
    void jwtTokenShouldHaveValidStructure() throws Exception {
        String email = generateUniqueEmail();
        
        // Rejestracja
        UserDTO userDTO = new UserDTO();
        userDTO.setUsername(TEST_USERNAME + "_jwt");
        userDTO.setEmail(email);
        userDTO.setPassword(TEST_PASSWORD);
        
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userDTO)));

        // Logowanie
        LoginDTO loginDto = new LoginDTO();
        loginDto.setEmail(email);
        loginDto.setPassword(TEST_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andReturn();

        String jsonResponse = result.getResponse().getContentAsString();
        
        // JWT powinien mieć 3 części oddzielone kropkami
        String token = jsonResponse.substring(jsonResponse.indexOf("\"token\":\"") + 9, jsonResponse.lastIndexOf("\""));
        String[] parts = token.split("\\.");
        
        assert parts.length == 3 : "JWT powinien mieć 3 części (header.payload.signature)";
    }

    @Test
    @Order(10)
    @DisplayName("Powinien zwrócić 409 przy próbie rejestracji użytkownika z istniejącym emailem")
    void shouldReturn409WhenRegisterWithDuplicateEmail() throws Exception {
        // Użyj unikalnego emaila dla tego testu, aby uniknąć konfliktów z poprzednimi uruchomieniami
        String duplicateEmail = generateUniqueEmail();
        
        //Pierwsza rejestracja - powinna się udać
        UserDTO firstUser = new UserDTO();
        firstUser.setUsername("first_user");
        firstUser.setEmail(duplicateEmail);
        firstUser.setPassword("FirstPassword123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstUser)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(duplicateEmail));

        // Druga rejestracja z tym samym emailem - powinna zwrócić 409 CONFLICT
        UserDTO secondUser = new UserDTO();
        secondUser.setUsername("second_user");
        secondUser.setEmail(duplicateEmail); // Ten sam email co pierwszy użytkownik
        secondUser.setPassword("SecondPassword456");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondUser)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(containsString("już istnieje")));
    }

    @AfterEach
    void tearDown() {
        // Opcjonalne czyszczenie po testach
    }
}



