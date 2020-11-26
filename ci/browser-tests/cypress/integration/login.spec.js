context('Login', () => {
    beforeEach(() => {
      cy.visit("")
    })

    it("dummy login as benjamin boss should work", () => {

        cy.server()
        cy.route('POST', 'command*').as('command')

        cy.get("#password-textfield").type(Cypress.env("SITE_PASSWORD"))
        cy.get("button").contains("Login as Benjamin Boss").click()
        //cy.wait('@command')

        // User should be taken to map page after login
        cy.contains('Minu projektid');
    })

})
