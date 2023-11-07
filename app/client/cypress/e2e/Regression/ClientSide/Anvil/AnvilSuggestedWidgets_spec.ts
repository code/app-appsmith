import { ANVIL_EDITOR_TEST } from "../../../../support/Constants";
import { agHelper, dataSources } from "../../../../support/Objects/ObjectsCore";
import { Widgets } from "../../../../support/Pages/DataSources";
import * as viewWidgets from "../../../../locators/ViewWidgets.json";

describe(`${ANVIL_EDITOR_TEST}:Check Suggested Widgets Feature`, function () {
  it("1. Suggested wds widgets for anvil layout", () => {
    dataSources.CreateDataSource("Postgres");
    dataSources.CreateQueryAfterDSSaved("SELECT * FROM configs LIMIT 10;");
    cy.intercept("/api/v1/actions/execute", {
      fixture: "addWidgetTable-mock",
    });
    dataSources.RunQuery({ toValidateResponse: false });
    dataSources.AddSuggestedWidget(Widgets.WDSTable);
  });
});
