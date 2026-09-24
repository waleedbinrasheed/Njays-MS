package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.master.MasterDataDtos.ProductRequest;
import com.inventory.dto.master.MasterDataDtos.ProductResponse;
import com.inventory.entity.Category;
import com.inventory.entity.Product;
import com.inventory.entity.Publisher;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.MasterDataMapper;
import com.inventory.repository.CategoryRepository;
import com.inventory.repository.ProductRepository;
import com.inventory.repository.PublisherRepository;
import com.inventory.validation.Isbn;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static com.inventory.repository.ProductSpecifications.active;
import static com.inventory.repository.ProductSpecifications.category;
import static com.inventory.repository.ProductSpecifications.matches;
import static com.inventory.repository.ProductSpecifications.publisher;
import static com.inventory.service.MasterDataSupport.checkVersion;
import static com.inventory.service.MasterDataSupport.deleteIfUnused;
import static com.inventory.service.MasterDataSupport.trimToNull;

@Service
public class ProductService {

    private final ProductRepository products;
    private final PublisherRepository publishers;
    private final CategoryRepository categories;

    public ProductService(ProductRepository products, PublisherRepository publishers, CategoryRepository categories) {
        this.products = products;
        this.publishers = publishers;
        this.categories = categories;
    }

    public record ProductFilter(String search, Boolean active, Long publisherId, Long categoryId) {
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(ProductFilter filter, Pageable pageable) {
        Specification<Product> spec = Specification.allOf(
                matches(filter.search()), active(filter.active()),
                publisher(filter.publisherId()), category(filter.categoryId()));
        return PageResponse.of(products.findAll(spec, pageable), MasterDataMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return MasterDataMapper.toResponse(load(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String isbn = Isbn.normalize(request.isbn());
        if (isbn != null && products.existsByIsbn(isbn)) {
            throw duplicateIsbn(isbn);
        }
        Product product = new Product();
        apply(product, request, isbn);
        return MasterDataMapper.toResponse(products.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = load(id);
        checkVersion(product, request.version());
        String isbn = Isbn.normalize(request.isbn());
        if (isbn != null && products.existsByIsbnAndIdNot(isbn, id)) {
            throw duplicateIsbn(isbn);
        }
        apply(product, request, isbn);
        return MasterDataMapper.toResponse(products.saveAndFlush(product));
    }

    /** Products with stock history or sales are protected by foreign keys (Phase 4+). */
    @Transactional
    public void delete(Long id) {
        Product product = load(id);
        deleteIfUnused(products, product, "Product '" + product.getTitle() + "'");
    }

    private void apply(Product p, ProductRequest r, String isbn) {
        p.setIsbn(isbn);
        p.setTitle(r.title().trim());
        p.setAuthor(trimToNull(r.author()));
        p.setPublisher(resolvePublisher(r.publisherId(), p.getPublisher()));
        p.setCategory(resolveCategory(r.categoryId(), p.getCategory()));
        p.setEdition(trimToNull(r.edition()));
        p.setPublicationYear(r.publicationYear() == null ? null : r.publicationYear().shortValue());
        p.setPurchasePrice(r.purchasePrice());
        p.setSellingPrice(r.sellingPrice());
        p.setActive(r.active() == null || r.active());
    }

    /** A newly chosen publisher must be active; keeping an existing (now inactive) one is allowed. */
    private Publisher resolvePublisher(Long id, Publisher current) {
        if (id == null) return null;
        if (current != null && Objects.equals(current.getId(), id)) return current;
        Publisher publisher = publishers.findById(id).orElseThrow(() -> new BusinessException("Selected publisher does not exist"));
        if (!publisher.isActive()) {
            throw new BusinessException("Publisher '" + publisher.getName() + "' is inactive");
        }
        return publisher;
    }

    private Category resolveCategory(Long id, Category current) {
        if (id == null) return null;
        if (current != null && Objects.equals(current.getId(), id)) return current;
        Category category = categories.findById(id).orElseThrow(() -> new BusinessException("Selected category does not exist"));
        if (!category.isActive()) {
            throw new BusinessException("Category '" + category.getName() + "' is inactive");
        }
        return category;
    }

    private Product load(Long id) {
        return products.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private static BusinessException duplicateIsbn(String isbn) {
        return new BusinessException(HttpStatus.CONFLICT, "A product with ISBN " + isbn + " already exists");
    }
}
