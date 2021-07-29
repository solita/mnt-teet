context("Contracts MM", () => {
    beforeEach(() => {
        cy.dummyLogin("benjamin");
        cy.selectLanguage("#EN");
    });

    it("finds contracts with search and navigates there + edits details + project link", () => {
        findContract("TEPPO");

        cy.get('h1 + button').contains("Edit").click({force: true});
        cy.formInput(":thk.contract/number", "123",
                     ":thk.contract/external-link", "https://example.com",
                     ":thk.contract/signed-at", "16.06.2021",
                     ":thk.contract/start-of-work", "17.06.2021",
                     ":thk.contract/deadline", "18.06.2021",
                     ":thk.contract/extended-deadline", "19.06.2021",
                     ":thk.contract/cost", "599");
        cy.formSubmit();
        cy.get("[data-cy='snackbar-success']");
        cy.get("[data-cy='contract-related-link']").click({force: true})
        cy.url().should('contain', 'projects/');
    })

    it("ensure that the contract has no related contracts", () => {
        findContract("TEPPO");
        cy.contains("Related contracts").should('not.exist');
    })

    function findContract(contractName) {
        cy.visit("#/contracts");
        cy.get("[data-cy='search-shortcut-all-contracts']").click({force: true});
        // cy.get("[data-cy='toggle-filters-visibility']").click({force: true}); // uncomment if default is hidden, has been in flux
        cy.get("input[id='contract-filter-input/:contract-name']").type(contractName);
        cy.get("[data-cy='contract-card'] h4").contains(contractName, {matchCase: false}).parent().find("a[href^=\"#/contracts/\"]").click();
        cy.url().should('contain', 'contracts/');
    }

})
