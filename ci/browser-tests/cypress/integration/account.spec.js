context("Account page", () => {
    before(() => {
        cy.dummyLogin("Danny");

        // Navigate to the account page

        cy.get("#open-account-navigation").click();
        cy.get("#account-page-link").click();
        cy.get(".account-page-heading");
    })

    it("Change account email", () => {
        cy.wait(100);
        cy.get('[data-form-attribute=":user/email"] input').invoke("val").then((val) => {

            expect(val).to.match(/^\S+@\S+$/);

            cy.get('[data-form-attribute=":user/email"] input').clear();

            cy.formInput(
                ":user/email", val + "-test")

            cy.formSubmit();

            cy.get("form button.submit").should("be.disabled");

            cy.get('[data-form-attribute=":user/email"] input').clear();

            cy.formInput(
                ":user/email", val)

            cy.formSubmit();

            cy.get("form button.submit").should("be.disabled");

        });

    })

})
