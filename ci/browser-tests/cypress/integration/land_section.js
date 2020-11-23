context('Login', () => {
  beforeEach(() => {
      cy.server();
      cy.route('POST', 'command*').as('command')

      cy.visit("/")
      cy.get("#password-textfield").type(Cypress.env("SITE_PASSWORD"));
      cy.get("button").contains("Login as Benjamin Boss").click();
      
      cy.contains('Minu projektid');      
    })

  it("select project", () => {
    cy.get("span").contains("Projektide loetelu").click();
    /*
    - doesn't find the element for unknown reason:
    cy.get("a").contains("Kõik projektid").click();
    */
    cy.visit('/#/projects/list?row-filter=all')    

    cy.get('thead > tr > th:nth-child(1) > label > div > input').type("aruvalla");
    cy.get("p").contains("Aruvalla").click();
    cy.get("main span.MuiIconButton-label > span.material-icons").click();
    cy.get("body > div:nth-child(3) > div > ul > li:nth-child(6)").click();
    // cy.get("a", {timeout: 20000}).contains("Vaatan omanike andmeid").click();
    cy.get("div.MuiPaper-rounded > div > div > div:nth-child(2) > div:nth-child(2) > div:nth-child(1) > div > div > div > a", {timeout: 20000}).contains("Vaatan omanike andmeid").click();
    cy.get("h2").contains("Omanikud ja omandiosad");
    // empty dialog as of this writing, can continue test when dialog impl is there
  });

})
