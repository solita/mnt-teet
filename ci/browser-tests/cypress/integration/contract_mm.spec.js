// todo: use test data instead of using aws dev data
context("Contracts MM", () => {
    before(() => {
        cy.dummyLogin("benjamin")
        cy.visit("#/contracts")
    })

    it("finds contracts with search and navigates there", () => {
        cy.get("button").contains("All contracts").click();

        cy.get("button").contains("Show filters").click();

        cy.get("#contract-filter-input/contract-name").type("ÜKS");

        cy.get("h4 + a[href^=\"#/contracts/\"").click();

        cy.url().should('contain', 'contracts/');
    })

})
