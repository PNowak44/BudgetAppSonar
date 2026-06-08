package pk.np.pasir_nowak_pawel.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class InfoController {

    @GetMapping("/api/info")
    public Map<String, String> getInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("appName", "Aplikacja Budżetowa");
        info.put("message", "Witaj w aplikacji budżetowej stworzonej ze Spring Boot!");
        info.put("version", "1.0");
        return info;
    }
}