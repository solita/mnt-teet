context("Contracts MM", () => {
    before(() => {
        cy.dummyLogin("benjamin");
        cy.visit("#/contracts");
        cy.selectLanguage("#EN");
    });

    it("finds contracts with search and navigates there", () => {
        cy.get("[data-cy='search-shortcut-all-contracts']").click();
        // cy.get("[data-cy='toggle-filters-visibility']").click(); // uncomment if default is hidden, has been in flux
        cy.get("input[id='contract-filter-input/:contract-name']").type("TEPPO");

        // cy.get("[data-cy='expand-contracts']").click({force: true});
        // cy.get("[data-cy='expand-contracts'] span").first().click();
        cy.get("[data-cy='expand-contracts']").click();


        cy.get('h6:contains("TA Project manager")').should('be.visible');

        cy.get("h4 + a[href^=\"#/contracts/\"").click();

        cy.url().should('contain', 'contracts/');
    });

    it("edits contract", () => {
        cy.selectLanguage("#EN");
        cy.get('h1 + button').contains("Edit").click();
        cy.formInput(":thk.contract/number", "123",
                     ":thk.contract/external-link", "https://example.com",
                     ":thk.contract/signed-at", "16.06.2021",
                     ":thk.contract/start-of-work", "17.06.2021",
                     ":thk.contract/deadline", "18.06.2021",
                     ":thk.contract/extended-deadline", "19.06.2021",
                     ":thk.contract/cost", "599");
        cy.formSubmit()
        // fixme: this passes even though video shows it's failing on a session
        // timeout, seemingly connected to use of formInput
        cy.get(".MuiSnackbar-root")

    });
})
