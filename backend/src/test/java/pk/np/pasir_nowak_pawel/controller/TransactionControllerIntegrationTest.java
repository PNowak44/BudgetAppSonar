package pk.np.pasir_nowak_pawel.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
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
import pk.np.pasir_nowak_pawel.dto.TransactionDTO;
import pk.np.pasir_nowak_pawel.dto.UserDTO;
import pk.np.pasir_nowak_pawel.repository.UserRepository;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransactionControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String pawelToken;
    private String tomaszToken;
    private Integer pawelFirstTransactionId;
    private Integer tomaszFirstTransactionId;
    private Integer pawelSecondTransactionId;
    private Integer tomaszSecondTransactionId;

    @BeforeEach
    void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext)
                .apply(springSecurity())
                .build();
        userRepository.deleteAll();

        pawelToken = registerAndLogin("pawelTest", "pawel@pk.pl", "pawel123");
        tomaszToken = registerAndLogin("tomaszTest", "tomasz@pk.pl", "tomasz123");

        pawelFirstTransactionId = createTransactionAndGetId(pawelToken, 10000.0, "INCOME", "Wypłata, Miesięczna", "Pensja za miesiąc pracy");
        tomaszFirstTransactionId = createTransactionAndGetId(tomaszToken, 15000.0, "INCOME", "Premia, Roczna", "Premia pod koniec roku");
        pawelSecondTransactionId = createTransactionAndGetId(pawelToken, 1220.0, "EXPENSE", "Smartwatch", "Zakup zegarka elektrycznego");
        tomaszSecondTransactionId = createTransactionAndGetId(tomaszToken, 75.0, "EXPENSE", "Jedzenie", "Zakupy spożywcze");
    }

    private String registerAndLogin(String username, String email, String password) throws Exception {
        UserDTO userDTO = new UserDTO();
        userDTO.setUsername(username);
        userDTO.setEmail(email);
        userDTO.setPassword(password);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userDTO)));

        LoginDTO loginDto = new LoginDTO();
        loginDto.setEmail(email);
        loginDto.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).get("token").asText();
    }

    private Integer createTransactionAndGetId(String token, Double amount, String type, String tags, String notes) throws Exception {
        TransactionDTO transactionDTO = new TransactionDTO();
        transactionDTO.setAmount(amount);
        transactionDTO.setType(type);
        transactionDTO.setTags(tags);
        transactionDTO.setNotes(notes);

        MvcResult result = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transactionDTO)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).get("id").asInt();
    }

    @Test
    @Order(1)
    @DisplayName("2.1 Dodaj transakcję - Paweł (INCOME)")
    void shouldAddTransactionIncome_Pawel() throws Exception {
        TransactionDTO transactionDTO = new TransactionDTO();
        transactionDTO.setAmount(5000.0);
        transactionDTO.setType("INCOME");
        transactionDTO.setTags("Wypłata");
        transactionDTO.setNotes("Pensja");

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + pawelToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transactionDTO)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.type").value("INCOME"));
    }

    @Test
    @Order(2)
    @DisplayName("2.2 Dodaj transakcję - Paweł (EXPENSE)")
    void shouldAddTransactionExpense_Pawel() throws Exception {
        TransactionDTO transactionDTO = new TransactionDTO();
        transactionDTO.setAmount(250.0);
        transactionDTO.setType("EXPENSE");
        transactionDTO.setTags("Paliwo");
        transactionDTO.setNotes("Orlen");

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + pawelToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transactionDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.type").value("EXPENSE"));
    }

    @Test
    @Order(3)
    @DisplayName("2.3 Dodaj transakcję - Tomasz (INCOME)")
    void shouldAddTransactionIncome_Tomasz() throws Exception {
        TransactionDTO transactionDTO = new TransactionDTO();
        transactionDTO.setAmount(1000.0);
        transactionDTO.setType("INCOME");
        transactionDTO.setTags("Wypłata,tygodniowa");
        transactionDTO.setNotes("Pensja za tydzień pracy");

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + tomaszToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transactionDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.type").value("INCOME"));
    }

    @Test
    @Order(4)
    @DisplayName("2.4 Zły - Dodaj BEZ logowania (błąd 401/403)")
    void shouldFailToAddTransactionWithoutLogin() throws Exception {
        TransactionDTO transactionDTO = new TransactionDTO();
        transactionDTO.setAmount(15000.0);
        transactionDTO.setType("INCOME");
        transactionDTO.setTags("Premia, roczna");
        transactionDTO.setNotes("Premia za cały rok pracy");

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transactionDTO)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    @DisplayName("2.5 Zły - Dodaj z błędnymi danymi (walidacja)")
    void shouldFailToAddTransactionWithInvalidData() throws Exception {
        TransactionDTO transactionDTO = new TransactionDTO();
        transactionDTO.setAmount(-300.0);
        transactionDTO.setType("SPRZEDAZ");
        transactionDTO.setTags("");
        transactionDTO.setNotes("");

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + pawelToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transactionDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    @DisplayName("3.1 Pobierz wszystkie - Paweł")
    void shouldGetAllTransactions_Pawel() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + pawelToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].id").value(pawelFirstTransactionId));
    }

    @Test
    @Order(7)
    @DisplayName("3.2 Pobierz wszystkie - Tomasz")
    void shouldGetAllTransactions_Tomasz() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + tomaszToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].id").value(tomaszFirstTransactionId));
    }

    @Test
    @Order(8)
    @DisplayName("3.3 Zły - Pobierz BEZ logowania (błąd 401/403)")
    void shouldFailToGetAllTransactionsWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(9)
    @DisplayName("3.4 Pobierz po ID - Paweł")
    void shouldGetTransactionById_Pawel() throws Exception {
        mockMvc.perform(get("/api/transactions/" + pawelSecondTransactionId)
                        .header("Authorization", "Bearer " + pawelToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pawelSecondTransactionId));
    }

    @Test
    @Order(10)
    @DisplayName("3.5 Zły - Pobierz transakcję Tomasza jako Paweł (błąd)")
    void shouldFailToGetTomaszTransactionAsPawel() throws Exception {
        mockMvc.perform(get("/api/transactions/" + tomaszSecondTransactionId)
                        .header("Authorization", "Bearer " + pawelToken))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(11)
    @DisplayName("4.1 Zaktualizuj transakcję - Paweł (właściciel)")
    void shouldUpdateTransaction_Pawel() throws Exception {
        TransactionDTO updateData = new TransactionDTO();
        updateData.setAmount(12000.0);
        updateData.setType("INCOME");
        updateData.setTags("Tag");
        updateData.setNotes("Zaktualizowana notatka");

        mockMvc.perform(put("/api/transactions/" + pawelFirstTransactionId)
                        .header("Authorization", "Bearer " + pawelToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(12000.0))
                .andExpect(jsonPath("$.tags").value("Tag"))
                .andExpect(jsonPath("$.notes").value("Zaktualizowana notatka"));
    }

    @Test
    @Order(12)
    @DisplayName("4.2 Zły - Zaktualizuj transakcję Tomasza jako Paweł")
    void shouldFailToUpdateTomaszTransactionAsPawel() throws Exception {
        TransactionDTO updateData = new TransactionDTO();
        updateData.setAmount(125.0);
        updateData.setType("EXPENSE");
        updateData.setTags("Zaktualizowany tag");
        updateData.setNotes("Zaktualizowana notatka");

        mockMvc.perform(put("/api/transactions/" + tomaszSecondTransactionId)
                        .header("Authorization", "Bearer " + pawelToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(13)
    @DisplayName("4.3 Zły - Zaktualizuj BEZ logowania (błąd)")
    void shouldFailToUpdateTransactionWithoutLogin() throws Exception {
        TransactionDTO updateData = new TransactionDTO();
        updateData.setAmount(999.0);
        updateData.setType("EXPENSE");
        updateData.setTags("Zaktualizowany tag");
        updateData.setNotes("Zaktualizowana notatka");

        mockMvc.perform(put("/api/transactions/" + pawelSecondTransactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(14)
    @DisplayName("5.3 Usuń transakcję - Paweł (właściciel)")
    void shouldDeleteTransaction_Pawel() throws Exception {
        mockMvc.perform(delete("/api/transactions/" + pawelFirstTransactionId)
                        .header("Authorization", "Bearer " + pawelToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/transactions/" + pawelFirstTransactionId)
                        .header("Authorization", "Bearer " + pawelToken))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(15)
    @DisplayName("5.1 Zły - Usuń transakcję Tomasza jako Paweł")
    void shouldFailToDeleteTomaszTransactionAsPawel() throws Exception {
        mockMvc.perform(delete("/api/transactions/" + tomaszFirstTransactionId)
                        .header("Authorization", "Bearer " + pawelToken))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(16)
    @DisplayName("5.2 Zły - Usuń BEZ logowania (błąd)")
    void shouldFailToDeleteTransactionWithoutLogin() throws Exception {
        mockMvc.perform(delete("/api/transactions/" + pawelFirstTransactionId))
                .andExpect(status().isForbidden());
    }
}