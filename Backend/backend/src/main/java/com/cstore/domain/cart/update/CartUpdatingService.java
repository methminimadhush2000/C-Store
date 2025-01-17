package com.cstore.domain.cart.update;

import com.cstore.dao.cart.CartDao;
import com.cstore.dao.cart.item.CartItemDao;
import com.cstore.dao.inventory.InventoryDao;
import com.cstore.dao.variant.VariantDao;
import com.cstore.dto.VariantProperiesDto;
import com.cstore.exception.NoSuchVariantException;
import com.cstore.model.cart.CartItem;
import com.cstore.model.product.Variant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CartUpdatingService {
    private final CartDao cartDao;
    private final CartItemDao cartItemDao;
    private final InventoryDao inventoryDao;
    private final VariantDao variantDao;

    public List<CartItemDto> getItems(Long userId) {

        List<CartItem> items = cartItemDao.findByUserId(userId);

        List<CartItemDto> response = new ArrayList<>();
        for (CartItem item : items) {
            CartItemDto cartItem = CartItemDto
                .builder()
                .variantId(item.getVariantId())
                .count(item.getCount())
                .build();

            response.add(cartItem);
        }

        return response;

    }

    public Long addVariant(Long userId, VariantProperiesDto properties) {

        List<Long> propertyIds = properties.getPropertyIds();
        Set<Variant> variants = new HashSet<Variant>();

        for (Long propertyId : propertyIds) {
            Set<Variant> tempVariants = new HashSet<>(variantDao.findByPropertyId(propertyId));

            if (variants.isEmpty()) {
                variants = tempVariants;
            } else {
                variants.retainAll(tempVariants);
            }
        }

        if (variants.size() != 1) {
            throw new NoSuchVariantException("No unique variant with the given set of properties found.");
        }

        Variant variant = variants.iterator().next();
        cartDao.addToCart(userId, variant.getVariantId(), properties.getQuantity());

        return variant.getVariantId();

    }

    public CartItemDto updateVariant(Long userId, CartItemUpdateRequest request) {

        Optional<CartItem> item = cartItemDao.findByUIdAndVId(userId, request.getVariantId());

        if (item.isEmpty()) {
            throw new NoSuchVariantException(
                "User with id " + userId + " does not have a variant with id " + request.getVariantId() + "in his cart."
            );
        }

        if (request.getNewCount() > 0) {
            cartItemDao.updateCount(userId, request.getVariantId(), request.getNewCount());
        }
        else {
            cartItemDao.deleteItem(userId, request.getVariantId());
        }

        return CartItemDto
            .builder()
            .variantId(request.getVariantId())
            .count(request.getNewCount())
            .build();

    }

    public List<CartItemDto> refresh(Long userId, List<CartItemDto> cartItems) {

        // TODO: Find if the user's cart contains the variants in CARTITEMS.

        List<CartItemDto> erroneousCartItems = new ArrayList<>();

        for (CartItemDto cartItemDto : cartItems) {
            Integer availableCount = inventoryDao.findCountByVariantId(cartItemDto.getVariantId());

            if (availableCount == null) {
                cartItemDto.setCount(0);
                erroneousCartItems.add(cartItemDto);
            }
            else if (availableCount < cartItemDto.getCount()) {
                cartItemDto.setCount(availableCount);
                erroneousCartItems.add(cartItemDto);
            }
        }

        return erroneousCartItems;
    }

}