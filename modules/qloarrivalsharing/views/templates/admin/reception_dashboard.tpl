<div class="panel">
    <div class="panel-heading">
        <i class="icon-map-marker"></i> {l s='Painel de Recepção e Monitoramento de Traslados' mod='qloarrivalsharing'}
    </div>
    <div class="panel-body">
        <div class="alert alert-info">
            <i class="icon-info-circle"></i>
            {l s='Serviço de detecção de proximidade ativo para monitoramento de chegadas.' mod='qloarrivalsharing'}
        </div>
        <div class="well">
            <h4><i class="icon-building"></i> {l s='Coordenadas da Propriedade' mod='qloarrivalsharing'}</h4>
            <p><strong>{l s='Latitude' mod='qloarrivalsharing'}:</strong> {$hotelLat|escape:'html':'UTF-8'}</p>
            <p><strong>{l s='Longitude' mod='qloarrivalsharing'}:</strong> {$hotelLng|escape:'html':'UTF-8'}</p>
        </div>
    </div>
</div>
