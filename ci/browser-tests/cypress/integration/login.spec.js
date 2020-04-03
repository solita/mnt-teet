context('Login', () => {
    beforeEach(() => {
        // do any before setup here
        cy.visit("https://dev-teet.solitacloud.fi/")
    })

    it("dummy login as benjamin boss should work", () => {

        cy.get("#password-textfield").type(Cypress.env("SITE_PASSWORD"))
        cy.get("button").contains("Login as Boss").click()

        // User should be taken to map page after login
        cy.title().should('eq','Kaart')
    })

})
