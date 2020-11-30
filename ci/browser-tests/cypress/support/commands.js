// ***********************************************
// This example commands.js shows you how to
// create various custom commands and overwrite
// existing commands.
//
// For more comprehensive examples of custom
// commands please read more here:
// https://on.cypress.io/custom-commands
// ***********************************************
//
//
// -- This is a parent command --
Cypress.Commands.add("dummyLogin", (name) => {
    cy.visit("#/login")
    cy.get("#password-textfield").type(Cypress.env("SITE_PASSWORD"))
    cy.get("button").contains("Login as " + name).click()

    // check that logout link is in header
    cy.get("header a.header-logout")
})

// Create random name with prefix and assign it
Cypress.Commands.add("randomName", (contextName, prefix) => {
    const r = Math.floor(Math.random() * 100000)
    const randomName = `${prefix}${r}`
    console.log("WRAP ", randomName, " AS ", contextName)
    cy.wrap(randomName).as(contextName);
})

// Input to TEET form text input
Cypress.Commands.add("formInput", (...attrAndText) => {
    for(let i = 0; i < attrAndText.length/2; i++) {
        let attr = attrAndText[i*2+0]
        let text = attrAndText[i*2+1]
        console.log(attrAndText)
        cy.get(`div[data-form-attribute='${attr}'] input`).type(text)
    }
})

Cypress.Commands.add("formSubmit", () => {
    cy.get("form button.submit").click()
})

Cypress.Commands.add("formCancel", () => {
    cy.get("form button.cancel").click()
})

//
//
// -- This is a child command --
// Cypress.Commands.add("drag", { prevSubject: 'element'}, (subject, options) => { ... })
//
//
// -- This is a dual command --
// Cypress.Commands.add("dismiss", { prevSubject: 'optional'}, (subject, options) => { ... })
//
//
// -- This will overwrite an existing command --
// Cypress.Commands.overwrite("visit", (originalFn, url, options) => { ... })
