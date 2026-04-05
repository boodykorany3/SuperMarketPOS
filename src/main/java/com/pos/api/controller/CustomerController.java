package com.pos.api.controller;

import com.pos.api.dto.CustomerRequestDto;
import com.pos.api.dto.CustomerResponseDto;
import com.pos.api.dto.CustomerPointsAddRequestDto;
import com.pos.api.dto.CustomerPointsRedeemRequestDto;
import com.pos.api.dto.CustomerPointsRedeemResponseDto;
import com.pos.api.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public List<CustomerResponseDto> getAll() {
        return customerService.getAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponseDto create(@Valid @RequestBody CustomerRequestDto request) {
        return customerService.create(request);
    }

    @GetMapping("/phone/{phone}/points")
    public int getPoints(@PathVariable("phone") String phone) {
        return customerService.getPointsByPhone(phone);
    }

    @GetMapping("/points")
    public Map<String, Integer> getAllPoints() {
        return customerService.getAllPoints();
    }

    @PostMapping("/points/add")
    public int addPoints(@Valid @RequestBody CustomerPointsAddRequestDto request) {
        return customerService.addPoints(request);
    }

    @PostMapping("/points/redeem")
    public CustomerPointsRedeemResponseDto redeemPoints(@Valid @RequestBody CustomerPointsRedeemRequestDto request) {
        return customerService.redeemPoints(request);
    }
}
