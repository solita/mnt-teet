// todo: use test data instead of using aws dev data
context("Contracts MM", () => {
    before(() => {
        cy.dummyLogin("benjamin");
        cy.visit("#/contracts");
        cy.selectLanguage("#EN");
    })

    it("finds contracts with search and navigates there", () => {
        cy.get("button").contains("All contracts").click();

        cy.get("input[id='contract-filter-input/:contract-name']").type("ÜKS");

        cy.get("button").contains("Expand all").click();

        cy.get('h6:contains("TA Project manager")').should('be.visible');

        cy.get("h4 + a[href^=\"#/contracts/\"").click();

        cy.url().should('contain', 'contracts/');
    })

})
