context('Login', () => {
    beforeEach(() => {
      cy.visit("")
    })

    it("dummy login as benjamin boss should work", () => {

        // let's make sure we're using the language we're asserting in?
        cy.selectLanguage("ET")

        cy.get("#password-textfield").type(Cypress.env("SITE_PASSWORD"))
        cy.get("button").contains("Login as Benjamin Boss").click()
        //cy.wait('@command')

        // User should be taken to map page after login
        cy.contains('Minu projektid');
    })

})
