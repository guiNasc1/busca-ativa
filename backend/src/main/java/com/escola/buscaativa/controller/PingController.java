package com.escola.buscaativa.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class PingController {

    @GetMapping("/ping")
    public Map<String, String> ping(){
        log.info("Ping Recebido");
        return Map.of("status", "ok");
    }

}
