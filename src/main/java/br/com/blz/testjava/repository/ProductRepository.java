package br.com.blz.testjava.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import br.com.blz.testjava.exception.InvalidIdException;
import br.com.blz.testjava.exception.NullProductException;
import br.com.blz.testjava.exception.ProductIdAlreadyInUseException;
import br.com.blz.testjava.exception.ProductNotExistentException;
import br.com.blz.testjava.model.Inventory;
import br.com.blz.testjava.model.Product;
import br.com.blz.testjava.model.Warehouse;
import br.com.blz.testjava.validator.ProductValidator;

@Component
@SuppressWarnings({ "rawtypes", "unchecked" })
public class ProductRepository {
	private static final Logger LOG = LoggerFactory.getLogger(ProductRepository.class);
	
	private static Map<Long, Product> productRepo;
	private static AtomicLong id;
	
	@Autowired
	public ProductRepository() {
		if(productRepo == null)
			productRepo = new ConcurrentHashMap(); // to avoid null keys
		
		if(id == null)
			id = new AtomicLong(1);
	}
	
	public Product insert(Product newProduct) {
		if(newProduct == null)
			throw new NullProductException("Product must not be null");
		
		Long id = checkId(newProduct);
		ProductValidator.validate(newProduct);
		
		if(productRepo.get(newProduct.getSku()) != null)
			throw new ProductIdAlreadyInUseException("There is another product stored with the informed id.");
		
		if(newProduct.getInventory() == null) {
			newProduct.setInventory(initInventory());
		}
		
		productRepo.put(id, newProduct);
		return newProduct;
	}

	private Inventory initInventory() {
		Inventory inventory = new Inventory();
		inventory.setQuantity(0L);
		inventory.setWarehouses(Collections.EMPTY_LIST);
		return inventory;
	}

	private Long checkId(Product newProduct) {
		Long sku = newProduct.getSku();
		if(ObjectUtils.isEmpty(sku)) {
			LOG.error("Null id informed. A new one will be generated");
			sku = id.getAndIncrement() ;
			newProduct.setSku(sku);
		}
		else if(negativeId(sku)) {
			throw new InvalidIdException("Unable to insert product due invalid id");
		}
		
		return sku;
	}

	private boolean isInvalidId(Long id) {
		return ObjectUtils.isEmpty(id) || negativeId(id);
	}

	private boolean negativeId(Long id) {
		return id.longValue() < 1L;
	}

	public Product get(Long sku) {
		Product product = productRepo.get(sku);
		product = prepareProductToBeRetrieved(product);
		
		return product;
	}

	private Product prepareProductToBeRetrieved(Product product) {
		if(product == null) {
			throw new ProductNotExistentException("Product not found");
		}
		Inventory inventory = product.getInventory();
		
		long total = 0L;
		for(Warehouse ware : inventory.getWarehouses()) {
			total += ware.getQuantity();
		}
		inventory.setQuantity(total);
		product.setInventory(inventory);
		product.setIsMarketable(total > 0);
		
		return product;
	}

	public Product update(Product newProduct) {
		if(newProduct == null) {
			throw new ProductNotExistentException("Product to update was not found");
		}
		if(isInvalidId(newProduct.getSku())) {
			throw new InvalidIdException("Unable to update product due invalid id");
		}
		
		if(productRepo.get(newProduct.getSku()) == null) {
			throw new ProductNotExistentException("Product to update was not found");
		}
		
		ProductValidator.validate(newProduct);
		
		productRepo.replace(newProduct.getSku(), newProduct);
		return newProduct;
	}

	public Product delete(Long sku) {
		if(isInvalidId(sku) || get(sku) == null) 
			throw new ProductNotExistentException("Product to remove was not found");
		
		return productRepo.remove(sku);
	}

	public List<Long> testGetAll() {
		Iterator<Long> keys = productRepo.keySet().iterator();
		List<Long> keysList = new ArrayList();
		while(keys.hasNext()) {
			keysList.add(keys.next());
		}
		return keysList;
	}
}
