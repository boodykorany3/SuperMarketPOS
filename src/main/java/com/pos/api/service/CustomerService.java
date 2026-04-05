package com.pos.api.service;

import com.pos.api.dto.CustomerRequestDto;
import com.pos.api.dto.CustomerResponseDto;
import com.pos.api.dto.CustomerPointsAddRequestDto;
import com.pos.api.dto.CustomerPointsRedeemRequestDto;
import com.pos.api.dto.CustomerPointsRedeemResponseDto;
import com.pos.api.entity.Customer;
import com.pos.api.exception.ApiException;
import com.pos.api.repository.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<CustomerResponseDto> getAll() {
        return customerRepository.findAll().stream().map(this::toResponse).toList();
    }

    public CustomerResponseDto create(CustomerRequestDto request) {
        customerRepository.findByPhone(request.getPhone().trim())
                .ifPresent(c -> {
                    throw new ApiException(HttpStatus.CONFLICT, "Phone already exists.");
                });

        Customer customer = new Customer();
        customer.setName(request.getName().trim());
        customer.setPhone(request.getPhone().trim());
        customer.setAddress(request.getAddress() == null ? null : request.getAddress().trim());
        customer.setPoints(0);
        return toResponse(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public int getPointsByPhone(String phone) {
        return customerRepository.findByPhone(phone.trim())
                .map(c -> c.getPoints() == null ? 0 : c.getPoints())
                .orElse(0);
    }

    @Transactional
    public int addPoints(CustomerPointsAddRequestDto request) {
        String phone = request.getPhone().trim();
        Customer customer = customerRepository.findByPhoneForUpdate(phone)
                .orElseGet(() -> createLoyaltyCustomer(phone));

        int current = customer.getPoints() == null ? 0 : customer.getPoints();
        customer.setPoints(current + request.getValue());
        return customerRepository.save(customer).getPoints();
    }

    @Transactional
    public CustomerPointsRedeemResponseDto redeemPoints(CustomerPointsRedeemRequestDto request) {
        String phone = request.getPhone().trim();
        int cost = request.getCost();
        CustomerPointsRedeemResponseDto response = new CustomerPointsRedeemResponseDto();

        Customer customer = customerRepository.findByPhoneForUpdate(phone).orElse(null);
        if (customer == null) {
            response.setRedeemed(false);
            response.setPoints(0);
            return response;
        }

        int current = customer.getPoints() == null ? 0 : customer.getPoints();
        if (current < cost) {
            response.setRedeemed(false);
            response.setPoints(current);
            return response;
        }

        customer.setPoints(current - cost);
        Customer updated = customerRepository.save(customer);
        response.setRedeemed(true);
        response.setPoints(updated.getPoints() == null ? 0 : updated.getPoints());
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Integer> getAllPoints() {
        Map<String, Integer> points = new LinkedHashMap<>();
        for (Customer customer : customerRepository.findAll()) {
            points.put(customer.getPhone(), customer.getPoints() == null ? 0 : customer.getPoints());
        }
        return points;
    }

    public Customer requireById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer not found: " + id));
    }

    private CustomerResponseDto toResponse(Customer customer) {
        CustomerResponseDto dto = new CustomerResponseDto();
        dto.setId(customer.getId());
        dto.setName(customer.getName());
        dto.setPhone(customer.getPhone());
        dto.setAddress(customer.getAddress());
        dto.setPoints(customer.getPoints() == null ? 0 : customer.getPoints());
        return dto;
    }

    private Customer createLoyaltyCustomer(String phone) {
        Customer customer = new Customer();
        customer.setName("Customer " + phone);
        customer.setPhone(phone);
        customer.setAddress(null);
        customer.setPoints(0);
        return customerRepository.save(customer);
    }
}
