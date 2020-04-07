context('Login', () => {
    beforeEach(() => {
        // do any before setup here
        cy.visit("https://dev-teet.solitacloud.fi/")


    })

    it("dummy login as benjamin boss should work", () => {

        cy.server()
        cy.route('POST', 'command*').as('command')

        cy.get("#password-textfield").type(Cypress.env("SITE_PASSWORD"))
        cy.get("button").contains("Login as Boss").click()
        //cy.wait('@command')

        // User should be taken to map page after login
        cy.title({timeout: 15000}).should('eq','Kaart')
    })

})
