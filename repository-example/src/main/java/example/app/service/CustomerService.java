/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package example.app.service;

import static example.app.model.Contact.newContact;

import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import example.app.model.Address;
import example.app.model.Contact;
import example.app.model.Customer;
import example.app.model.PhoneNumber;
import example.app.model.support.Identifiable;
import example.app.repo.gemfire.ContactRepository;
import example.app.repo.gemfire.CustomerRepository;

/**
 * The CustomerService class...
 *
 * @author John Blum
 * @since 1.0.0
 */
@Service("customerService")
@SuppressWarnings("unused")
public class CustomerService {

	protected static final Pattern EMAIL_PATTERN =
		Pattern.compile("[a-zA-Z0-9_]+@[a-zA-Z0-9_]+(\\.com|\\.net|\\.org|\\.edu)");

	@Autowired
	private ContactRepository contactRepository;

	@Autowired
	private CustomerRepository customerRepository;

	protected String newAccountNumber() {
		return UUID.randomUUID().toString();
	}

	protected long newId() {
		return System.currentTimeMillis();
	}

	protected <T extends Identifiable<Long>> T setId(T identifiable) {
		if (identifiable.isNew()) {
			identifiable.setId(newId());
		}

		return identifiable;
	}

	@Transactional
	public Customer createAccount(Customer customer) {
		Assert.state(customer.getAccountNumber() == null,
			String.format("Customer [%s] already has an account", customer));

		return customerRepository.save(setId(customer.with(newAccountNumber())));
	}

	@Transactional
	public Customer createAccountIfNotExists(Customer customer) {
		Customer existingCustomer = (customer.getAccountNumber() != null
			? customerRepository.findByAccountNumber(customer.getAccountNumber())
			: (customer.getId() != null ? customerRepository.findOne(customer.getId())
			: null));

		if (existingCustomer == null) {
			existingCustomer = customer.with(newAccountNumber()).with(newId());
			customerRepository.save(existingCustomer);
		}

		return existingCustomer;
	}

	@Transactional(readOnly = true)
	public Contact findContactInformation(Customer customer) {
		Assert.notNull(customer, "Customer cannot be null");

		return (customer.getId() == null ? null : contactRepository.findByPersonId(customer.getId()));
	}

	protected Contact saveContactInformation(Customer customer, Function<Contact, Contact> customerContactFunction) {
		return contactRepository.save(customerContactFunction.apply(findContactInformation(
			createAccountIfNotExists(customer))));
	}

	@Transactional
	public Contact addContactInformation(Customer customer, Address address) {
		return saveContactInformation(customer, (Contact customerContact) ->
			customerContact != null ? customerContact.with(address)
				: newContact(customer, address).with(newId()));
	}

	@Transactional
	public Contact addContactInformation(Customer customer, String email) {
		return saveContactInformation(customer, (Contact customerContact) ->
			customerContact != null ? customerContact.with(validateEmail(email))
				: newContact(customer, validateEmail(email)).with(newId()));
	}

	@Transactional
	public Contact addContactInformation(Customer customer, PhoneNumber phoneNumber) {
		return saveContactInformation(customer, (Contact customerContact) ->
			customerContact != null ? customerContact.with(validatePhoneNumber(phoneNumber))
				: newContact(customer, validatePhoneNumber(phoneNumber)).with(newId()));
	}

	protected String validateEmail(String email) {
		Assert.isTrue(EMAIL_PATTERN.matcher(email).find(), String.format("email [%s] is invalid", email));
		return email;
	}

	protected PhoneNumber validatePhoneNumber(PhoneNumber phoneNumber) {
		Assert.isTrue(!"555".equals(phoneNumber.getPrefix()), String.format(
			"'555' is not a valid phone number [%s] exchange", phoneNumber));

		return phoneNumber;
	}
}
