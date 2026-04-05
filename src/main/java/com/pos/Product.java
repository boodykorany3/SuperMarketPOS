package com.pos;

public class Product {

    private Long id;
    private String name;
    private String barcode;
    private double price;
    private int quantity;

    public Product(String name, String barcode, double price, int quantity) {
        this(null, name, barcode, price, quantity);
    }

    public Product(Long id, String name, String barcode, double price, int quantity) {
        this.id = id;
        this.name = name;
        this.barcode = barcode;
        this.price = price;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
