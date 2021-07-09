describe("Cost items, totals and materials views", function() {
  let costItemOid
  let componentOid
  let materialOid
  let projectId

  beforeEach(() => {
    cy.dummyLogin("benjamin")
    cy.selectLanguage("#EN")
    // TODO store projectId
  })

  context("Cost items", function() {

    it("new cost item can be created", function() {
      cy.projectByName("integration test project")
      cy.location().then((loc) => {
        let matches = loc.hash.match(/#\/projects\/(\d+)/)
        projectId = matches[1]
      })
      cy.get("button.project-menu").click({force: true})
      cy.get("li#navigation-item-cost-items").click({force: true})

      cy.get("[data-cy='add-cost-item']").click()

      // Select Road drainage / Culvert
      cy.get("[data-cy='select-fgroup-and-fclass']").type("culvert")
      cy.get("div.select-user-entry.active").click()

      // Insert location information
      cy.get("[data-form-attribute=':location/start-point'] input").type("600236.0080478053,6557242.769895551")
      cy.get("[data-form-attribute=':location/end-point'] input").type("600274.183778265,6557247.541861858")

      cy.wait(1000)

      // Fill required data
      cy.get("select[id='links-type-select:common/status']").select("Planned")
      cy.get("select[id='links-type-select:culvert/culvertlocation']").select("Under main road")

      // Submit
      cy.get("button[type=submit]").click()

      // Check we're redirected to the cost item form, store cost item oid
      cy.get("h2[data-cy='cost-item-oid']").then(($h2) => {
        costItemOid = $h2.text()
      })

      // Check that the saved values correspond to what was input
      cy.get("[data-form-attribute=':location/start-point'] input").should("have.value", "600236.0080478053,6557242.769895551")
      cy.get("[data-form-attribute=':location/end-point'] input").should("have.value", "600274.183778265,6557247.541861858")
      cy.get("select[id='links-type-select:common/status'] option:selected").should("have.text", "Planned")
      cy.get("select[id='links-type-select:culvert/culvertlocation']  option:selected").should("have.text", "Under main road")

    })

    it("cost item can be accessed using the left panel navigation", function() {
      cy.visit("#/projects/" + projectId + "/cost-items/")
      // Navigate to the created culvert cost item via the navigation on the left panel
      cy.get("[data-cy='navigation-fgroup-drainage'] button").click()
      cy.get("[data-cy='navigation-fclass-culvert'] a").contains(costItemOid).click()
      cy.get("[data-cy='cost-item-oid']").contains(costItemOid)
    })

    it("cost item can be edited", function() {
      cy.visit("#/projects/" + projectId + "/cost-items/" + costItemOid)

      // Check that error message is shown when trying to input invalid value
      cy.get("[data-form-attribute=':culvert/culvertlength'] input").type("not a number")
      cy.get("[data-cy='form-field-error-tooltip']").contains("Insert decimal value")
      // Currently when form data contains errors, the submit button is enabled but clicking on it does nothing
      // cy.get("button[type=submit]").should('be.disabled')

      cy.get("[data-form-attribute=':culvert/culvertlength'] input").clear().type("10")
      cy.get("button[type=submit]").click()

      // Currently there's no success message to wait for
      cy.wait(1000)
      cy.reload()
      cy.get("[data-form-attribute=':culvert/culvertlength'] input").should("have.value", "10")
    })


    it("component can be added to the cost item", function() {
      cy.visit("#/projects/" + projectId + "/cost-items/" + costItemOid)

      // Add a culvert pipe component
      cy.get("button[data-cy='add-culvertpipe']").click()
      cy.get("[data-form-attribute=':culvertpipe/culvertpipediameter'] input").type("10")
      cy.get("[data-form-attribute=':component/quantity'] input").type("20")
      cy.get("select[id='links-type-select:common/status']").select("Planned")
      cy.get("[data-form-attribute=':culvertpipe/culvertpipelenght']").type("20")

      // Submit
      cy.get("button[type=submit]").click()

      // Check we're redirected to the component form, store component oid
      cy.get("h2[data-cy='component-oid']").then(($h2) => {
        componentOid = $h2.text()
      })

      // Check that the saved values correspond to what was input
      cy.get("[data-form-attribute=':culvertpipe/culvertpipediameter'] input").should("have.value", "10")
      cy.get("[data-form-attribute=':component/quantity'] input").should("have.value", "20")
      cy.get("select[id='links-type-select:common/status'] option:selected").should("have.text", "Planned")
      cy.get("[data-form-attribute=':culvertpipe/culvertpipelenght'] input").should("have.value", "20")
    })

    it("component can be edited", function() {
      cy.visit("#/projects/" + projectId + "/cost-items/" + componentOid)

      cy.get("button[type=submit]").should("be.disabled")

      // If nothing has changed, the save button should be disabled
      cy.get("[data-form-attribute=':culvertpipe/culvertpipediameter'] input").clear().type("10")
      cy.get("button[type=submit]").should("be.disabled")

      // Edit culvert pipe diameter
      cy.get("[data-form-attribute=':culvertpipe/culvertpipediameter'] input").clear().type("20")

      // Submit
      cy.get("button[type=submit]").click()

      // Check the edited diameter is saved correctly
      cy.wait(1000)
      cy.reload()
      cy.get("[data-form-attribute=':culvertpipe/culvertpipediameter'] input").should("have.value", "20")
    })


    it("material can be added to the added component", function() {
      // Open the component page
      cy.visit("#/projects/" + projectId + "/cost-items/" + componentOid)

      // Add concrete material
      cy.get("button[data-cy='component-menu-button']").click()
      cy.get("li[data-cy='add-concrete']").click()
      cy.get("[data-form-attribute=':concrete/concreteclass'] select").select("C25")
      cy.get("[data-form-attribute=':concrete/concreteenvironmentalclass'] select").select("X0")

      // Submit
      cy.get("button[type=submit]").click()

      // Check we're redirected to the material form, store material oid
      cy.get("h2[data-cy='material-oid']").then(($h2) => {
        materialOid = $h2.text()
      })

      cy.get("[data-form-attribute=':concrete/concreteclass'] select option:selected").should("have.text", "C25")
      cy.get("[data-form-attribute=':concrete/concreteenvironmentalclass'] select option:selected").should("have.text", "X0")

    })

    it("material can edited", function() {
      // Open the material page
      cy.visit("#/projects/" + projectId + "/cost-items/" + materialOid)

      // Change concrete class
      cy.get("[data-form-attribute=':concrete/concreteclass'] select").select("C35")

      // Submit
      cy.get("button[type=submit]").click()

      // Check that concrete class is saved correctly
      cy.wait(1000)
      cy.reload()
      cy.get("[data-form-attribute=':concrete/concreteclass'] select option:selected").should("have.text", "C35")
    })

    it("component is shown in cost items totals", function() {
      cy.visit("#/projects/" + projectId + "/cost-items-totals")
      cy.get("[data-cy='navigation-fgroup-drainage'] button").click()
      cy.get("[data-cy='navigation-fclass-culvert'] a").click()

      // Add cost per quantity of 100
      cy.get("tr[data-cy='row-Concrete_class:C35;Environmental_class:X0;Opening_diameter:20,00'] [data-cy='table-body-column-cost-per-quantity-unit'] input")
        .clear().type("100").blur()
      cy.get("tr[data-cy='row-Concrete_class:C35;Environmental_class:X0;Opening_diameter:20,00'] [data-cy='table-body-column-cost-per-quantity-unit'] input")
        .should("have.value", "100,00")
    })

    it("material can be deleted", function() {
      // Open the component page
      cy.visit("#/projects/" + projectId + "/cost-items/" + componentOid)

      // The material is listed
      cy.get(`a[href='#/projects/${projectId}/cost-items/${materialOid}']`).should("exist")
      // Delete material
      cy.get("button").contains("Delete").click()
      cy.get("button#confirm-delete").click()

      // The material is not listed any more
      cy.get(`a[href='#/projects/${projectId}/cost-items/${materialOid}']`).should("not.exist")
    })

    it("component can be deleted", function() {
      // Open the cost item page
      cy.visit("#/projects/" + projectId + "/cost-items/" + costItemOid)

      // The cost item is listed
      cy.get(`a[href='#/projects/${projectId}/cost-items/${componentOid}']`).should("exist")
      // Delete cost item
      cy.get("button").contains("Delete").click()
      cy.get("button#confirm-delete").click()

      // The cost item is not listed any more
      cy.get(`a[href='#/projects/${projectId}/cost-items/${componentOid}']`).should("not.exist")
    })

  })
})
