package pk.np.pasir_nowak_pawel.controller;

import jakarta.validation.Valid;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import pk.np.pasir_nowak_pawel.dto.GroupTransactionDTO;
import pk.np.pasir_nowak_pawel.model.User;
import pk.np.pasir_nowak_pawel.service.CurrentUserService;
import pk.np.pasir_nowak_pawel.service.GroupTransactionService;

@Controller
public class GroupTransactionGraphQLController {

    private final GroupTransactionService groupTransactionService;
    private final CurrentUserService currentUserService;

    public GroupTransactionGraphQLController(
            GroupTransactionService groupTransactionService,
            CurrentUserService currentUserService) {
        this.groupTransactionService = groupTransactionService;
        this.currentUserService = currentUserService;
    }

    @MutationMapping
    public Boolean addGroupTransaction(@Valid @Argument GroupTransactionDTO groupTransactionDTO) {
        User user = currentUserService.getCurrentUser();
        groupTransactionService.addGroupTransaction(groupTransactionDTO, user);
        return true;
    }
}