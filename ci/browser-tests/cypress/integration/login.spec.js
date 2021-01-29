context('Login', () => {
    beforeEach(() => {
        cy.visit("")

        // wait for initial rendering
        cy.get("header", {timeout: 30000})
    })

    it("dummy login as benjamin boss should work", () => {

        cy.get("#password-textfield").type(Cypress.env("SITE_PASSWORD"))
        cy.get("button").contains("Login as Benjamin Boss").click()
        cy.wait(100)

        // User should be taken to map page after login
        cy.get('[data-cy="dashboard-header"]').should("exist");
    })

})
