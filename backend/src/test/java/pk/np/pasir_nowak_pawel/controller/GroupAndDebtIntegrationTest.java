package pk.np.pasir_nowak_pawel.controller;

import com.fasterxml.jackson.databind.JsonNode;
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
import pk.np.pasir_nowak_pawel.repository.DebtRepository;
import pk.np.pasir_nowak_pawel.repository.GroupRepository;
import pk.np.pasir_nowak_pawel.repository.MembershipRepository;
import pk.np.pasir_nowak_pawel.repository.UserRepository;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GroupAndDebtIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private MembershipRepository membershipRepository;
    @Autowired
    private DebtRepository debtRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String ownerToken;
    private String member1Token;
    private String member2Token;
    private String outsiderToken;

    private Integer ownerId;
    private Integer member1Id;
    private Integer member2Id;
    private Integer outsiderId;

    private final String ownerEmail = "owner.test@pk.pl";
    private final String member1Email = "member1.test@pk.pl";
    private final String member2Email = "member2.test@pk.pl";
    private final String outsiderEmail = "outsider.test@pk.pl";

    @BeforeEach
    void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext)
                .apply(springSecurity())
                .build();

        debtRepository.deleteAll();
        membershipRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        ownerToken = registerAndLogin("ownerTest", ownerEmail, "owntst123");
        ownerId = getUserIdByEmail(ownerEmail);

        member1Token = registerAndLogin("member1Test", member1Email, "mem1tst123");
        member1Id = getUserIdByEmail(member1Email);

        member2Token = registerAndLogin("member2Test", member2Email, "mem2tst123");
        member2Id = getUserIdByEmail(member2Email);

        outsiderToken = registerAndLogin("outsiderTest", outsiderEmail, "outtst123");
        outsiderId = getUserIdByEmail(outsiderEmail);
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

    private Integer getUserIdByEmail(String email) {
        return userRepository.findByEmail(email).get().getId().intValue();
    }

    private String performGraphql(String token, String query) throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("query", query);
        MvcResult result = mockMvc.perform(post("/graphql")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private Integer createGroup(String token, String name) throws Exception {
        String query = String.format("mutation { createGroup(groupDTO: {name: \"%s\"}) { id } }", name);
        String response = performGraphql(token, query);
        return objectMapper.readTree(response).at("/data/createGroup/id").asInt();
    }

    private void addMemberToGroup(String token, Integer groupId, String userEmail) throws Exception {
        String mutation = String.format("mutation { addMember(membershipDTO: {groupId: \"%d\", userEmail: \"%s\"}) { id } }", groupId, userEmail);
        performGraphql(token, mutation);
    }

    private Integer getMembershipId(String token, Integer groupId, String email) throws Exception {
        String query = String.format("query { groupMembers(groupId: \"%d\") { id userEmail } }", groupId);
        String response = performGraphql(token, query);
        JsonNode members = objectMapper.readTree(response).at("/data/groupMembers");
        for (JsonNode member : members) {
            if (member.get("userEmail").asText().equals(email)) {
                return member.get("id").asInt();
            }
        }
        return null;
    }

    private Integer createDebt(String token, Integer groupId, Integer debtorId, Integer creditorId, Double amount) throws Exception {
        String mutation = String.format("mutation { createDebt(debtDTO: {groupId: \"%d\", debtorId: \"%d\", creditorId: \"%d\", amount: %s, title: \"Testowy\"}) { id } }",
                groupId, debtorId, creditorId, amount);
        String response = performGraphql(token, mutation);
        return objectMapper.readTree(response).at("/data/createDebt/id").asInt();
    }

    @Test
    @Order(1)
    @DisplayName("Utworzenie grupy dodaje właściciela jako członka i zwraca ją w myGroups")
    void shouldCreateGroupAndAddOwnerAsMember() throws Exception {
        createGroup(ownerToken, "grupaTest");

        String query = "query { myGroups { name } }";
        String response = performGraphql(ownerToken, query);

        assertTrue(response.contains("grupaTest"));
    }

    @Test
    @Order(2)
    @DisplayName("Tylko właściciel grupy może dodawać członków")
    void shouldAllowOnlyGroupOwnerToAddMembers() throws Exception {
        Integer groupId = createGroup(ownerToken, "grupaTest");

        String addMutation = String.format("mutation { addMember(membershipDTO: {groupId: \"%d\", userEmail: \"%s\"}) { id } }", groupId, member1Email);
        String responseFail = performGraphql(outsiderToken, addMutation);
        assertTrue(responseFail.contains("Tylko właściciel grupy") || responseFail.contains("error"));

        String responseSuccess = performGraphql(ownerToken, addMutation);
        assertTrue(responseSuccess.contains("addMember"));
    }

    @Test
    @Order(3)
    @DisplayName("groupMembers zwraca członków grupy tylko członkowi tej grupy")
    void shouldReturnGroupMembersOnlyToGroupMember() throws Exception {
        Integer groupId = createGroup(ownerToken, "grupaTest");
        addMemberToGroup(ownerToken, groupId, member1Email);

        String query = String.format("query { groupMembers(groupId: \"%d\") { userEmail } }", groupId);

        String responseSuccess = performGraphql(member1Token, query);
        assertTrue(responseSuccess.contains(ownerEmail));

        String responseFail = performGraphql(outsiderToken, query);
        assertTrue(responseFail.contains("nie jest członkiem") || responseFail.contains("error"));
    }

    @Test
    @Order(4)
    @DisplayName("groupDebts zwraca długi grupy tylko członkowi tej grupy")
    void shouldReturnGroupDebtsOnlyToGroupMember() throws Exception {
        Integer groupId = createGroup(ownerToken, "grupaTest");
        addMemberToGroup(ownerToken, groupId, member1Email);

        String query = String.format("query { groupDebts(groupId: \"%d\") { id } }", groupId);

        String responseSuccess = performGraphql(member1Token, query);
        assertFalse(responseSuccess.contains("errors"));

        String responseFail = performGraphql(outsiderToken, query);
        assertTrue(responseFail.contains("nie jest członkiem") || responseFail.contains("error"));
    }

    @Test
    @Order(5)
    @DisplayName("Nowy członek dostaje tylko długi z transakcji dodanych po dołączeniu")
    void shouldReturnOnlyNewDebtsForNewMember() throws Exception {
        Integer groupId = createGroup(ownerToken, "grupaTest");
        addMemberToGroup(ownerToken, groupId, member1Email);

        String addTransaction = String.format("mutation { addGroupTransaction(groupTransactionDTO: {groupId: \"%d\", amount: 100.0, type: \"EXPENSE\", title: \"Paliwo\"}) }", groupId);
        performGraphql(ownerToken, addTransaction);

        addMemberToGroup(ownerToken, groupId, member2Email);

        String query = String.format("query { groupDebts(groupId: \"%d\") { debtor { id } creditor { id } } }", groupId);
        String response = performGraphql(member2Token, query);

        assertFalse(response.contains("\"id\":\"" + member2Id + "\""));
    }

    @Test
    @Order(6)
    @DisplayName("Transakcja grupowa typu INCOME tworzy długi od aktualnego użytkownika do pozostałych członków")
    void shouldCreateDebtsFromCurrentUserToOthersForIncomeTransaction() throws Exception {
        Integer groupId = createGroup(ownerToken, "grupaTest");
        addMemberToGroup(ownerToken, groupId, member1Email);

        String addIncome = String.format("mutation { addGroupTransaction(groupTransactionDTO: {groupId: \"%d\", amount: 300.0, type: \"INCOME\", title: \"Wypłata wspólna\"}) }", groupId);
        performGraphql(ownerToken, addIncome);

        String query = String.format("query { groupDebts(groupId: \"%d\") { debtor { id } amount } }", groupId);
        String response = performGraphql(ownerToken, query);

        assertTrue(response.contains("\"id\":\"" + ownerId + "\""));
        assertTrue(response.contains("150.0"));
    }

    @Test
    @Order(7)
    @DisplayName("Usunięcie członka nie usuwa jego historycznych długów")
    void shouldNotDeleteHistoricalDebtsWhenMemberIsRemoved() throws Exception {
        Integer groupId = createGroup(ownerToken, "grupaTest");
        addMemberToGroup(ownerToken, groupId, member1Email);

        createDebt(ownerToken, groupId, member1Id, ownerId, 55.0);

        Integer membershipId = getMembershipId(ownerToken, groupId, member1Email);
        String removeMutation = String.format("mutation { removeMember(membershipId: \"%d\") }", membershipId);
        performGraphql(ownerToken, removeMutation);

        String query = String.format("query { groupDebts(groupId: \"%d\") { debtor { email } amount } }", groupId);
        String response = performGraphql(ownerToken, query);

        assertTrue(response.contains(member1Email));
        assertTrue(response.contains("55.0"));
    }

    @Test
    @Order(8)
    @DisplayName("Nie można usunąć właściciela z jego grupy przez removeMember")
    void shouldNotAllowRemovingOwnerFromGroup() throws Exception {
        Integer groupId = createGroup(ownerToken, "grupaTest");
        Integer ownerMembershipId = getMembershipId(ownerToken, groupId, ownerEmail);

        String removeMutation = String.format("mutation { removeMember(membershipId: \"%d\") }", ownerMembershipId);
        String response = performGraphql(ownerToken, removeMutation);

        assertTrue(response.contains("Nie można usunąć właściciela") || response.contains("error"));
    }

    @Test
    @Order(9)
    @DisplayName("Członek grupy niebędący właścicielem nie może usunąć grupy")
    void shouldNotAllowNonOwnerToDeleteGroup() throws Exception {
        Integer groupId = createGroup(ownerToken, "grupaTest");
        addMemberToGroup(ownerToken, groupId, member1Email);

        String deleteMutation = String.format("mutation { deleteGroup(id: \"%d\") }", groupId);
        String response = performGraphql(member1Token, deleteMutation);

        assertTrue(response.contains("Tylko właściciel grupy") || response.contains("error"));
    }

    @Test
    @Order(10)
    @DisplayName("createDebt tworzy ręczny dług tylko między członkami tej samej grupy")
    void shouldCreateManualDebtBetweenGroupMembers() throws Exception {
        Integer groupId = createGroup(ownerToken, "grupaTest");
        addMemberToGroup(ownerToken, groupId, member1Email);

        Integer debtId = createDebt(ownerToken, groupId, member1Id, ownerId, 100.0);
        assertNotNull(debtId);
        assertTrue(debtId > 0);
    }

    @Test
    @Order(11)
    @DisplayName("createDebt odrzuca użytkownika spoza grupy i dług do samego siebie")
    void shouldRejectCreateDebtWithOutsideUserAndSelfDebt() throws Exception {
        Integer groupId = createGroup(ownerToken, "grupaTest");

        String selfMutation = String.format("mutation { createDebt(debtDTO: {groupId: \"%d\", debtorId: \"%d\", creditorId: \"%d\", amount: 10.0, title: \"x\"}) { id } }", groupId, ownerId, ownerId);
        String selfResponse = performGraphql(ownerToken, selfMutation);
        assertTrue(selfResponse.contains("różnymi użytkownikami") || selfResponse.contains("error"));

        String outsideMutation = String.format("mutation { createDebt(debtDTO: {groupId: \"%d\", debtorId: \"%d\", creditorId: \"%d\", amount: 10.0, title: \"y\"}) { id } }", groupId, outsiderId, ownerId);
        String outsideResponse = performGraphql(ownerToken, outsideMutation);
        assertTrue(outsideResponse.contains("error") || outsideResponse.contains("nie jest"));
    }

    @Test
    @Order(12)
    @DisplayName("Właściciel grupy może utworzyć dług między innymi członkami grupy")
    void shouldAllowOwnerToCreateDebtBetweenOtherMembers() throws Exception {
        Integer groupId = createGroup(ownerToken, "grupaTest");
        addMemberToGroup(ownerToken, groupId, member1Email);
        addMemberToGroup(ownerToken, groupId, member2Email);

        Integer debtId = createDebt(ownerToken, groupId, member1Id, member2Id, 75.0);
        assertNotNull(debtId);
    }

    @Test
    @Order(13)
    @DisplayName("Członek grupy może utworzyć dług tylko gdy jest jego uczestnikiem")
    void shouldAllowMemberToCreateDebtOnlyWhenParticipant() throws Exception {
        Integer groupId = createGroup(ownerToken, "grupaTest");
        addMemberToGroup(ownerToken, groupId, member1Email);
        addMemberToGroup(ownerToken, groupId, member2Email);

        String failMutation = String.format("mutation { createDebt(debtDTO: {groupId: \"%d\", debtorId: \"%d\", creditorId: \"%d\", amount: 20.0, title: \"a\"}) { id } }", groupId, member2Id, ownerId);
        String failResponse = performGraphql(member1Token, failMutation);

        assertTrue(failResponse.contains("error") || failResponse.contains("uczestnik"));

        String successMutation = String.format("mutation { createDebt(debtDTO: {groupId: \"%d\", debtorId: \"%d\", creditorId: \"%d\", amount: 20.0, title: \"b\"}) { id } }", groupId, member1Id, member2Id);
        String successResponse = performGraphql(member1Token, successMutation);
        assertFalse(successResponse.contains("errors"));
    }

    @Test
    @Order(14)
    @DisplayName("deleteDebt usuwa dług dostępny dla uczestnika długu")
    void shouldDeleteDebtWhenUserIsParticipant() throws Exception {
        Integer groupId = createGroup(ownerToken, "grupaTest");
        addMemberToGroup(ownerToken, groupId, member1Email);

        Integer debtId = createDebt(ownerToken, groupId, member1Id, ownerId, 25.0);

        String deleteMutation = String.format("mutation { deleteDebt(debtId: \"%d\") }", debtId);
        String response = performGraphql(member1Token, deleteMutation);

        assertTrue(response.contains("\"deleteDebt\":true"));
    }

    @Test
    @Order(15)
    @DisplayName("deleteDebt odrzuca członka grupy, który nie jest właścicielem ani uczestnikiem długu")
    void shouldRejectDebtDeletionByNonParticipantAndNonOwner() throws Exception {
        Integer groupId = createGroup(ownerToken, "grupaTest");
        addMemberToGroup(ownerToken, groupId, member1Email);
        addMemberToGroup(ownerToken, groupId, member2Email);

        Integer debtId = createDebt(ownerToken, groupId, member1Id, ownerId, 45.0);

        String deleteMutation = String.format("mutation { deleteDebt(debtId: \"%d\") }", debtId);
        String response = performGraphql(member2Token, deleteMutation);

        assertTrue(response.contains("Tylko właściciel grupy") || response.contains("error"));
    }

    @Test
    @Order(16)
    @DisplayName("Właściciel grupy może usunąć dług, którego nie jest uczestnikiem")
    void shouldAllowOwnerToDeleteDebtBetweenOtherMembers() throws Exception {
        Integer groupId = createGroup(ownerToken, "grupaTest");
        addMemberToGroup(ownerToken, groupId, member1Email);
        addMemberToGroup(ownerToken, groupId, member2Email);

        Integer debtId = createDebt(ownerToken, groupId, member1Id, member2Id, 50.0);

        String deleteMutation = String.format("mutation { deleteDebt(debtId: \"%d\") }", debtId);
        String response = performGraphql(ownerToken, deleteMutation);

        assertTrue(response.contains("\"deleteDebt\":true"));
    }

    @Test
    @Order(17)
    @DisplayName("Walidacje danych wejściowych GraphQL odrzucają puste lub niepoprawne wartości")
    void shouldRejectEmptyOrInvalidDataInGraphql() throws Exception {
        String createGroupFail = "mutation { createGroup(groupDTO: {name: \"\"}) { id } }";
        String responseGroup = performGraphql(ownerToken, createGroupFail);
        assertTrue(responseGroup.contains("nie może być pusta") || responseGroup.contains("error"));

        Integer groupId = createGroup(ownerToken, "grupaTest");
        addMemberToGroup(ownerToken, groupId, member1Email);

        String debtMutationFail = String.format("mutation { createDebt(debtDTO: {groupId: \"%d\", debtorId: \"%d\", creditorId: \"%d\", amount: -15.0, title: \"Zły dług\"}) { id } }",
                groupId, member1Id, ownerId);
        String responseDebt = performGraphql(ownerToken, debtMutationFail);
        assertTrue(responseDebt.contains("musi być większa od zera") || responseDebt.contains("error"));
    }

    @Test
    @Order(18)
    @DisplayName("Usunięcie grupy przez właściciela usuwa powiązane długi i grupę")
    void shouldDeleteAssociatedDebtsAndMembershipsWhenGroupIsDeleted() throws Exception {
        Integer groupId = createGroup(ownerToken, "grupaTest");
        addMemberToGroup(ownerToken, groupId, member1Email);
        createDebt(ownerToken, groupId, member1Id, ownerId, 99.0);

        String deleteMutation = String.format("mutation { deleteGroup(id: \"%d\") }", groupId);
        performGraphql(ownerToken, deleteMutation);

        String query = "query { myGroups { id } }";
        String response = performGraphql(ownerToken, query);
        assertFalse(response.contains("\"id\":\"" + groupId + "\""));

        assertTrue(groupRepository.findById(Long.valueOf(groupId)).isEmpty());
        assertTrue(debtRepository.findByGroupId(Long.valueOf(groupId)).isEmpty());
        assertTrue(membershipRepository.findByGroupId(Long.valueOf(groupId)).isEmpty());
    }
}